# Auto Pending Round 04

## Goal

Use the repository's auto workflow to keep closing `docs/pending/review-06-action-items.md` without waiting for manual direction.

## This Round

1. Fix the remaining runtime correctness gaps in the projectile collision pipeline.
2. Close the last public review action items and leave only separate investigation notes.

## Scope

- `src/main/java/dev/xkmc/fastprojectileapi/collision/ProjectileHitHelper.java`
- `src/main/java/dev/xkmc/fastprojectileapi/entity/BaseProjectile.java`
- `src/main/java/dev/xkmc/fastprojectileapi/entity/ParallelDanmakuTicker.java`
- `src/main/java/dev/xkmc/youkaishomecoming/content/entity/danmaku/HitBehavior.java`
- `src/main/java/dev/xkmc/youkaishomecoming/content/entity/danmaku/ItemDanmakuEntity.java`
- `docs/pending/review-06-action-items.md`
- Round summary doc after verification

## Technical Plan

1. Switch projectile collision search to the current tick's pre-hit `computeMove()` result in both sequential and parallel paths.
2. Move projectile-specific hitbox shaping fully onto the Step 2 main-thread phase so Step 3 workers only read snapshots.
3. Audit in-repo `HitBehavior.CONTINUE` usage and document the runtime semantics explicitly.
4. Verify with `compileJava`, then close the remaining action items in the pending queue.
