# Auto Pending Round 03

## Goal

Use the repository's auto workflow to keep closing `docs/pending/review-06-action-items.md` without waiting for manual direction.

## This Round

1. Correct the remaining process/documentation drift around commit `b09e447`.
2. Make `/yhspell reload` explicitly describe its real runtime cost and scope.

## Scope

- `src/main/java/dev/xkmc/youkaishomecoming/events/YHCommands.java`
- `docs/pending/review-00-overview.md`
- `docs/pending/review-05-commits-7-to-13.md`
- `docs/plan-spellcard-rework-claude.md`
- `docs/pending/review-06-action-items.md`
- Round summary doc after verification

## Technical Plan

1. Update the command text for `/yhspell reload` so operators are told it performs a full datapack reload equivalent to `/reload`.
2. Correct the historical review/docs summaries for commit `b09e447` so they describe the actual mixed scope instead of the misleading narrow title.
3. Mark `P2-2` / `P2-3` resolved in the pending action list once the command and docs are aligned.
4. Re-run `compileJava`, then write the round summary and reassess what remains.
