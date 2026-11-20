#!/usr/bin/env bash
# Deletes the local branches whose pull request is already merged. GitHub deletes the remote branch on
# merge (delete_branch_on_merge), but never the copy in each clone, so they pile up one per pull request.
#
#   scripts/prune-local-branches.sh            prune, then list what was deleted and what was kept
#   scripts/prune-local-branches.sh --hook     the same, silent unless something was deleted; never fails
#
# .githooks/post-merge and .githooks/post-checkout run it with --hook after `git pull` and after switching
# to develop, so nobody has to remember. A branch is deleted only when all of these hold:
#   - its remote branch is gone,
#   - no worktree has it checked out,
#   - and its tip is either already in origin/develop, or exactly the head of a merged or closed pull
#     request (squash merging leaves the branch's commits out of develop, so the pull request is the proof).
# Anything else is kept, and the plain mode says why. Written for bash 3.2, like check-git-conventions.sh.

set -u

hook=false
[ "${1:-}" = --hook ] && hook=true

say() {
  $hook || printf '%s\n' "$1"
}

# A rebase or a merge in progress checks commits out on its own; pruning in the middle of it helps nobody.
for state in rebase-merge rebase-apply MERGE_HEAD; do
  [ -e "$(git rev-parse --git-path "$state")" ] && exit 0
done

# Forget remote branches GitHub already deleted. Offline, there is nothing new to learn: stop quietly.
git remote prune origin >/dev/null 2>&1 || exit 0

has_gh=false
command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1 && has_gh=true

git for-each-ref --format='%(refname:short)|%(upstream:track)|%(worktreepath)' refs/heads |
  while IFS='|' read -r branch track worktree; do
    case $branch in main | develop) continue ;; esac
    [ "$track" = '[gone]' ] || continue
    if [ -n "$worktree" ]; then
      say "kept $branch: checked out in $worktree"
      continue
    fi

    tip=$(git rev-parse "$branch")
    proof=''
    if git merge-base --is-ancestor "$tip" origin/develop 2>/dev/null; then
      proof='already in develop'
    elif $has_gh; then
      pr=$(gh pr list --state all --head "$branch" --limit 1 --json number,state,headRefOid \
        --jq '.[0] | select(.state == "MERGED" or .state == "CLOSED") | "\(.number) \(.state) \(.headRefOid)"' 2>/dev/null)
      if [ -n "$pr" ] && [ "${pr##* }" = "$tip" ]; then
        set -- $pr
        proof="#$1 $(printf '%s' "$2" | tr '[:upper:]' '[:lower:]')"
      fi
    fi

    if [ -z "$proof" ]; then
      say "kept $branch: its commits are in no merged pull request (push it, or delete it by hand)"
      continue
    fi
    git branch -D --quiet "$branch" &&
      printf 'deleted local branch %s (%s, was %s)\n' "$branch" "$proof" "$(git rev-parse --short "$tip")"
  done

exit 0
