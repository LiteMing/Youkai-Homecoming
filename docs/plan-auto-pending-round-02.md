# Auto Pending Round 02

## Goal

Use the repository's auto workflow to keep closing `docs/pending/review-06-action-items.md` without waiting for manual direction.

## This Round

1. Remove or narrow defensive code paths that currently hide runtime/editor failures.
2. Add explicit diagnostics for degraded homing / legacy ticker execution.
3. Prevent `HeightmapY` from loading chunks as a side effect.
4. Correct the pending documentation drift called out in `P4-1` and `P4-2`.

## Scope

- `src/main/java/dev/xkmc/youkaishomecoming/content/spell/preview/ActionEditorPanel.java`
- `src/main/java/dev/xkmc/youkaishomecoming/content/spell/action/LegacyTickerAction.java`
- `src/main/java/dev/xkmc/youkaishomecoming/content/spell/definition/MoverConfigs.java`
- `src/main/java/dev/xkmc/youkaishomecoming/content/spell/definition/NumberProviders.java`
- `docs/preview-performance-plan.md`
- `docs/GLM/spell-editor-migration-plan.md`
- `docs/pending/review-06-action-items.md`
- Round summary doc after verification

## Technical Plan

1. Replace the silent `ClassCastException` swallow in `notifySimple()` with a warning that preserves the current no-crash behavior but stops hiding future bugs.
2. Change `LegacyTickerAction` diagnostics from one global warning to per-spell warnings, and surface a one-time player-facing error message when a broken legacy ticker executes on the server.
3. Emit a warning when `HomingMoverConfig` is instantiated without `SpellContext`, so silent behavior degradation becomes visible.
4. Guard `HeightmapY` with `hasChunkAt()` and fall back to a safe Y value instead of force-loading chunks.
5. Update the affected docs to reflect the actual render-pipeline state and current `SpellItemForm` table shape.
6. Compile with Gradle, update the pending queue, and summarize the next round.
