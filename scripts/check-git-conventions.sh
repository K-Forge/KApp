#!/usr/bin/env bash
# The executable form of the git rules in CONTRIBUTING.md. CI runs it on every pull request into main
# or develop, and .githooks/commit-msg runs it before every local commit, so a wrong type or a commit
# on develop is caught on the laptop instead of in review.
#
#   scripts/check-git-conventions.sh commit-msg <file>      the message git is about to record (hook)
#   scripts/check-git-conventions.sh title "<text>"         a pull request title
#   scripts/check-git-conventions.sh branch <head> <base>   a pull request's branches
#   scripts/check-git-conventions.sh range <base> <head>    every non-merge commit in base..head
#   scripts/check-git-conventions.sh --self-test            the examples CONTRIBUTING.md gives
#
# Written for bash 3.2: macOS still ships it, and Git for Windows runs hooks in its own bash.
# Rules a script cannot judge, such as "fix: fixed the bug" being too vague, are left to review.

set -u

TYPES='feat|fix|chore|release|hotfix|docs|refactor|test'
# 72 is the git tradition and what fits git log --oneline; 100 is commitlint's default and what
# this team's narrative subjects need. The hard limit is 100, the guide asks people to aim for 72.
MAX_SUBJECT=100
CONTEXT=''

fail() {
  printf 'error: %s%s\n' "$CONTEXT" "$1" >&2
  return 1
}

matches() {
  printf '%s\n' "$1" | grep -qE "$2"
}

# The first line of a commit, or a pull request title: squash merging turns the title into the commit.
check_subject() {
  s=$1
  if matches "$s" '^[A-Za-z]+\([^)]*\)!?:'; then
    fail "\"$s\": no scopes. Write \"type: message\", not \"type(scope): message\"."
    return
  fi
  if ! matches "$s" '^[A-Za-z]+: [^ ]'; then
    fail "\"$s\": the format is \"type: message\", with one space after the colon."
    return
  fi

  type=${s%%:*}
  message=${s#*: }

  if ! matches "$type" "^($TYPES)$"; then
    lower=$(printf '%s' "$type" | tr '[:upper:]' '[:lower:]')
    if matches "$lower" "^($TYPES)$"; then
      fail "\"$s\": the type is lowercase, \"$lower\"."
    else
      fail "\"$s\": \"$type\" is not an allowed type. Use ${TYPES//|/, }. CONTRIBUTING.md (\"Que tipo uso?\") maps ci, build, style, design and platform work onto them."
    fi
    return
  fi
  if matches "$message" '^[A-Z]'; then
    fail "\"$s\": the message starts in lowercase."
    return
  fi
  if matches "$message" '\.$'; then
    fail "\"$s\": no final period."
    return
  fi
  if [ "${#s}" -gt "$MAX_SUBJECT" ]; then
    fail "\"$s\": ${#s} characters; the first line is at most $MAX_SUBJECT."
    return
  fi
}

check_branch_name() {
  case $1 in
    dependabot/*) return 0 ;;
    feat/*) fail "branch \"$1\": the prefix is feature/, not feat/."; return ;;
  esac
  if matches "$1" '^(feature|bugfix|chore|test|hotfix)/[a-z0-9]+(-[a-z0-9]+)*$'; then
    return 0
  fi
  if matches "$1" '^release/[0-9]+\.[0-9]+\.[0-9]+(-(alpha|beta|rc)\.[0-9]+)?$'; then
    return 0
  fi
  fail "branch \"$1\": use <type>/<kebab-case-description> with feature, bugfix, chore, test or hotfix, or release/X.Y.Z."
}

check_branch_target() {
  head=$1
  base=$2
  check_branch_name "$head" || return
  case $head in dependabot/*) return 0 ;; esac

  case $base in
    main)
      case ${head%%/*} in
        release | hotfix) ;;
        *) fail "\"$head\" cannot target main: only release/* and hotfix/* do. Open the pull request against develop." ;;
      esac
      ;;
    # Every valid prefix may target develop; release and hotfix return there after main. Any other
    # base is a branch built on an unmerged branch, which targets its parent until the parent merges.
    *) ;;
  esac
}

# Tooling commits under an address whose local part is noreply, or a name ending in [bot]. A member's
# own GitHub privacy address (<user>@users.noreply.github.com) is a real person and stays allowed.
check_author() {
  case $2 in
    *@users.noreply.github.com) return 0 ;;
  esac
  if matches "$2" '^no-?reply@' || matches "$1" '\[bot\]$'; then
    fail "author \"$1 <$2>\" is an automated identity. Commits are authored by the member who ran the session."
  fi
}

# The history records what the team did, not which tooling wrote it: no attribution trailers for tools.
check_trailers() {
  offender=$(printf '%s\n' "$1" | grep -iE '^co-authored-by:.*<[^>]*no-?reply@' | grep -viE '@users\.noreply\.github\.com>' | head -1)
  if [ -n "$offender" ]; then
    fail "the message credits an automated identity: $offender"
  fi
}

check_range() {
  if ! shas=$(git rev-list --no-merges --reverse "$1..$2"); then
    printf 'error: cannot read the commits in %s..%s\n' "$1" "$2" >&2
    exit 2
  fi

  status=0
  count=0
  for sha in $shas; do
    count=$((count + 1))
    CONTEXT="$(git rev-parse --short "$sha") "
    check_subject "$(git log -1 --format=%s "$sha")" || status=1
    check_author "$(git log -1 --format=%an "$sha")" "$(git log -1 --format=%ae "$sha")" || status=1
    check_trailers "$(git log -1 --format=%B "$sha")" || status=1
  done
  CONTEXT=''

  printf 'checked %d commit(s) in %s..%s\n' "$count" "$1" "$2"
  return "$status"
}

check_commit_msg() {
  git_dir=$(git rev-parse --git-dir)

  # Merges record git's own message and happen on the target branch by definition.
  if [ -f "$git_dir/MERGE_HEAD" ]; then
    return 0
  fi

  status=0
  branch=$(git symbolic-ref --quiet --short HEAD 2>/dev/null) || branch=''
  case $branch in
    '')
      # Detached HEAD is legitimate while a rebase, cherry-pick or revert rewrites history. Outside
      # those, a commit here belongs to no branch and is lost the moment someone switches away.
      if [ ! -d "$git_dir/rebase-merge" ] && [ ! -d "$git_dir/rebase-apply" ] &&
        [ ! -f "$git_dir/CHERRY_PICK_HEAD" ] && [ ! -f "$git_dir/REVERT_HEAD" ]; then
        fail "HEAD is detached, so this commit would belong to no branch. Start one: git switch -c feature/<description>"
        status=1
      fi
      ;;
    main | develop)
      fail "never commit on $branch. Start a branch from develop: git switch -c feature/<description>"
      status=1
      ;;
    *) check_branch_name "$branch" || status=1 ;;
  esac

  # An editor on Windows may save the message with CRLF; the \r would count as a character and hide a final period.
  message=$(tr -d '\r' <"$1" | grep -v '^#')
  subject=$(printf '%s\n' "$message" | awk 'NF { print; exit }')
  check_trailers "$message" || status=1
  case $subject in
    '' | 'fixup! '* | 'squash! '* | 'amend! '*) ;; # empty aborts on its own; autosquash markers are transient
    *) check_subject "$subject" || status=1 ;;
  esac

  if [ "$status" -ne 0 ]; then
    printf 'See CONTRIBUTING.md. The rejected message is kept in %s.\n' "$1" >&2
  fi
  return "$status"
}

self_test() {
  bad=0
  expect() {
    want=$1
    shift
    if "$@" 2>/dev/null; then got=pass; else got=fail; fi
    if [ "$got" != "$want" ]; then
      printf 'self-test: expected %s, got %s: %s\n' "$want" "$got" "$*" >&2
      bad=$((bad + 1))
    fi
  }

  # Correct examples from CONTRIBUTING.md
  expect pass check_subject 'feat: add login screen'
  expect pass check_subject 'fix: resolve jwt token expiration bug'
  expect pass check_subject 'chore: update spring boot dependencies'
  expect pass check_subject 'docs: add branching guide to contributing'
  expect pass check_subject 'refactor: extract user validation logic'
  expect pass check_subject 'test: add integration tests for user service'
  expect pass check_subject 'release: prepare version 1.0.0'
  expect pass check_subject 'hotfix: fix cors config in gateway'
  expect pass check_subject 'fix: read identity through CurrentUser, not a header'
  expect pass check_subject 'fix: revert the favicon cache headers'

  # Incorrect examples from CONTRIBUTING.md, and the ones this repository's history actually contains
  expect fail check_subject 'update'
  expect fail check_subject 'cambios'
  expect fail check_subject 'FEAT: Add product'
  expect fail check_subject 'feat(api): add product'
  expect fail check_subject 'feat: Add Product.'
  expect fail check_subject 'feat: add product.'
  expect fail check_subject 'feat:add product'
  expect fail check_subject 'Hotfix: fix cors config'
  expect fail check_subject 'design: draw the login screen'
  expect fail check_subject 'ios: start the swift client'
  expect fail check_subject 'wip: partial auth layer'
  expect fail check_subject 'ci: stop telling pnpm two different versions'
  expect fail check_subject 'Merge the MVP backend'
  expect pass check_subject 'fix: your name, e-mail and student code were the API examples, in a public repository'
  expect fail check_subject 'feat: add a subject that rambles well past the hard limit of one hundred characters, which is where a reader gives up'

  expect pass check_branch_target 'feature/student-dashboard' develop
  expect pass check_branch_target 'bugfix/fix-null-pointer-product' develop
  expect pass check_branch_target 'chore/update-spring-dependencies' develop
  expect pass check_branch_target 'hotfix/fix-cors-gateway' main
  expect pass check_branch_target 'hotfix/fix-cors-gateway' develop
  expect pass check_branch_target 'release/1.2.0' main
  expect pass check_branch_target 'release/1.0.0-beta.1' develop
  expect pass check_branch_target 'feature/home-kotlin' 'feature/frontend-kotlin'
  expect pass check_branch_target 'dependabot/npm_and_yarn/vite-6.0.1' develop

  expect fail check_branch_target 'mi-rama' develop
  expect fail check_branch_target 'feature/StudentDash' develop
  expect fail check_branch_target 'feat/login' develop
  expect fail check_branch_target 'feature/login' main
  expect fail check_branch_target 'develop' main
  expect fail check_branch_target 'release/next' main

  expect pass check_author 'Team Member' 'member@example.com'
  expect pass check_author 'Team Member' 'member@users.noreply.github.com'
  expect fail check_author 'Some Tool' 'noreply@vendor.example'
  expect fail check_author 'renovate[bot]' 'bot@vendor.example'

  expect pass check_trailers 'feat: add login screen'
  expect pass check_trailers 'feat: add login screen

Co-Authored-By: Team Member <member@example.com>'
  expect fail check_trailers 'feat: add login screen

Co-Authored-By: Some Tool <noreply@vendor.example>'

  if [ "$bad" -ne 0 ]; then
    printf 'self-test: %d case(s) wrong\n' "$bad" >&2
    return 1
  fi
  echo 'self-test: all cases pass'
}

usage() {
  sed -n '6,10p' "$0" | sed 's/^# *//' >&2
  exit 2
}

case ${1:-} in
  commit-msg) [ $# -eq 2 ] || usage; check_commit_msg "$2" ;;
  title) [ $# -eq 2 ] || usage; check_subject "$2" ;;
  branch) [ $# -eq 3 ] || usage; check_branch_target "$2" "$3" ;;
  range) [ $# -eq 3 ] || usage; check_range "$2" "$3" ;;
  --self-test) self_test ;;
  *) usage ;;
esac
