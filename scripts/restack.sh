#!/usr/bin/env bash
# Rebases the pull requests stacked on one that was just squash merged, so nobody has to run git
# between merges. .github/workflows/restack.yml runs it with the merged pull request's data:
#
#   PARENT_NUMBER    the merged pull request
#   PARENT_HEAD_REF  its head branch, which GitHub may already have deleted
#   PARENT_SHA       its head commit as it was merged
#   BASE_REF         the branch it was merged into, normally develop
#
# Why it is needed: squash merging writes a new commit with the same content, and the original
# commits never reach develop. A child branch still carries them, so Git sees two different edits
# to the same lines and reports conflicts that are not real. Replaying only the child's own
# commits, everything after PARENT_SHA, onto the new develop removes them.
#
# The rules it keeps:
#   - A push the author made in the meantime is never overwritten: every push is leased to the
#     commit read before rebasing.
#   - A real conflict leaves the branch exactly as it was and explains on the pull request how to
#     finish by hand. Its own descendants are then left alone too, since their base did not move.
#   - Authors and messages are kept; only the committer changes, so the conventions check still
#     passes on the rebased commits.
#   - It only runs git and gh. It never executes anything from the branches it rebases, because
#     it holds a token that can push.
#
# Exits 0 when a branch could not be rebased - the comment is the signal, and a red run on a
# merged pull request helps nobody. Exits non-zero only when it could not do its job at all.

set -eu

: "${PARENT_NUMBER:?}" "${PARENT_HEAD_REF:?}" "${PARENT_SHA:?}" "${BASE_REF:?}"
REMOTE=${REMOTE:-origin}
MAX_DEPTH=10

# Open pull requests, one per line: number, head branch, base branch.
OPEN_PRS=''

pr_list() {
  gh pr list --state open --limit 200 --json number,headRefName,baseRefName \
    --jq '.[] | [.number, .headRefName, .baseRefName] | @tsv'
}

pr_base() {
  gh pr view "$1" --json baseRefName --jq .baseRefName
}

pr_retarget() {
  gh pr edit "$1" --base "$2" >/dev/null
}

pr_comment() {
  gh pr comment "$1" --body-file - >/dev/null
}

# Rebases one branch and then, if that worked, the branches stacked on it.
#   $1 pull request number   $2 head branch      $3 depth
#   $4 branch to rebase onto $5 commit to exclude (everything reachable from it stays behind)
#   $6 base to retarget to, or empty to keep the current one
restack() {
  local number=$1 head=$2 depth=$3 onto=$4 exclude=$5 retarget=$6
  local old new files

  if [ "$depth" -gt "$MAX_DEPTH" ]; then
    echo "#$number: deeper than $MAX_DEPTH levels, left as it is"
    return 0
  fi

  if ! old=$(git rev-parse -q --verify "refs/remotes/$REMOTE/$head"); then
    echo "#$number: its branch $head no longer exists, skipped"
    return 0
  fi
  git checkout -q --detach "$old"

  if ! git rebase -q --onto "refs/remotes/$REMOTE/$onto" "$exclude" >/dev/null 2>&1; then
    files=$(git diff --name-only --diff-filter=U | sed 's/^/- `/; s/$/`/')
    [ -n "$files" ] || files='Git stopped before it could list them.'
    git rebase --abort 2>/dev/null || true
    echo "#$number: conflicts, left as it is"
    pr_comment "$number" <<EOF
The parent of this pull request, #$PARENT_NUMBER, was squash merged, and this branch could not be rebased onto \`$onto\` automatically. These files changed there too:

$files

The branch was left exactly as it was. To finish by hand:

\`\`\`bash
git fetch origin
git rebase --onto origin/$onto $exclude $head
# resolve, then
git push --force-with-lease
\`\`\`
EOF
    return 0
  fi

  new=$(git rev-parse HEAD)
  if [ "$new" = "$old" ]; then
    echo "#$number: already on $onto"
    if [ -n "$retarget" ] && [ "$(pr_base "$number")" != "$retarget" ]; then
      pr_retarget "$number" "$retarget"
    fi
    return 0
  fi
  if ! git push -q --force-with-lease="refs/heads/$head:$old" "$REMOTE" "HEAD:refs/heads/$head" 2>/dev/null; then
    echo "#$number: the branch moved while it was being rebased, left as it is"
    pr_comment "$number" <<EOF
This branch changed while it was being rebased after #$PARENT_NUMBER was squash merged, so nothing was pushed and your commits are as you left them. To rebase it by hand:

\`\`\`bash
git fetch origin
git rebase --onto origin/$onto $exclude $head
git push --force-with-lease
\`\`\`
EOF
    return 0
  fi
  # The descendants are rebased onto this new head.
  git update-ref "refs/remotes/$REMOTE/$head" "$new"

  if [ -n "$retarget" ] && [ "$(pr_base "$number")" != "$retarget" ]; then
    pr_retarget "$number" "$retarget"
  fi

  echo "#$number: rebased onto $onto"
  if [ "$depth" -eq 1 ]; then
    pr_comment "$number" <<EOF
#$PARENT_NUMBER was squash merged, so this branch was rebased onto \`$onto\` and now targets it. Only this branch's own commits were replayed, and its checks run again on the new head.

Before committing again, update your copy: \`git pull --rebase\`.
EOF
  else
    pr_comment "$number" <<EOF
After #$PARENT_NUMBER was squash merged, the branch this one builds on, \`$onto\`, was rebased, so this branch was rebased onto its new head. Its base is still \`$onto\`, and its checks run again.

Before committing again, update your copy: \`git pull --rebase\`.
EOF
  fi

  # Children of this branch still carry its old commits: exclude everything up to the old head.
  local child child_head child_base
  while IFS="$(printf '\t')" read -r child child_head child_base <&3; do
    [ "$child_base" = "$head" ] || continue
    restack "$child" "$child_head" $((depth + 1)) "$head" "$old" ''
  done 3<<EOF
$OPEN_PRS
EOF
}

main() {
  git fetch -q --prune "$REMOTE"

  # A merge commit or a fast-forward brings the parent's commits into the base, so the children
  # never conflict. Only a squash leaves them behind.
  if git merge-base --is-ancestor "$PARENT_SHA" "refs/remotes/$REMOTE/$BASE_REF"; then
    echo "#$PARENT_NUMBER was not squash merged into $BASE_REF: nothing to rebase"
    return 0
  fi

  OPEN_PRS=$(pr_list)

  # Direct children are found by history, not by base: with "delete branch on merge", GitHub may
  # already have retargeted them to BASE_REF by the time this runs. A branch is a child when it
  # contains the parent's head and targets either the parent or the parent's own base. Its own
  # children target it, so they wait for the cascade.
  local number head base found=0
  # The loops read the list on descriptor 3, so a command inside that reads stdin cannot eat it.
  while IFS="$(printf '\t')" read -r number head base <&3; do
    [ -n "$number" ] || continue
    [ "$head" != "$PARENT_HEAD_REF" ] || continue
    [ "$base" = "$PARENT_HEAD_REF" ] || [ "$base" = "$BASE_REF" ] || continue
    git rev-parse -q --verify "refs/remotes/$REMOTE/$head" >/dev/null || continue
    git merge-base --is-ancestor "$PARENT_SHA" "refs/remotes/$REMOTE/$head" || continue
    found=1
    restack "$number" "$head" 1 "$BASE_REF" "$PARENT_SHA" "$BASE_REF"
  done 3<<EOF
$OPEN_PRS
EOF

  if [ "$found" -eq 0 ]; then
    echo "#$PARENT_NUMBER had no pull requests stacked on it"
  fi
}

main
