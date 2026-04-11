# Auto Danmaku Perf Summary Round 01 Summary

## Completed

- Added cross-tick danmaku perf accumulation in `ParallelDanmakuTicker`, covering sample counts, parallel/sequential splits, projectile totals, prefetch totals, fallback/failure totals, and average/peak timing inputs.
- Extended `/danmaku perf` with `summary` and `reset`, so operators can inspect a sampling window and restart measurement before another profiling pass.
- Updated `docs/preview-performance-plan.md` and `docs/develop/latest-development-plan.md` so the optimization notes now describe both latest-frame and rolling-window observability.

## Verification

- Ran `.\gradlew.bat compileJava`
- Result: success
- Residual warning: `TouhouHatItem.onArmorTick` still uses a deprecated Forge API (pre-existing, unrelated to this round)

## Next Queue

- The instrumentation side of the current danmaku optimization follow-up is now closed.
- The next auto round should focus on 90k scenario measurement and on re-checking the remaining external-state mover boundaries.
