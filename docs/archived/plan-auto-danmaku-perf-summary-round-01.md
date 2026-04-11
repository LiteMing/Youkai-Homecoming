# Auto Danmaku Perf Summary Round 01

## Goal

Use the repository's auto workflow to finish the profiling-facing follow-up after the last-frame `/danmaku perf` command landed.

## This Round

1. Extend danmaku perf stats from a single latest-frame snapshot to a rolling multi-sample summary.
2. Expose command hooks that let operators inspect and reset the sampling window during 90k scenario profiling.
3. Update active planning docs so the next automation round can focus on measurement rather than more instrumentation plumbing.

## Scope

- `src/main/java/dev/xkmc/fastprojectileapi/entity/ParallelDanmakuTicker.java`
- `src/main/java/dev/xkmc/youkaishomecoming/events/YHCommands.java`
- `docs/preview-performance-plan.md`
- `docs/develop/latest-development-plan.md`
- `docs/archived/summary-auto-danmaku-perf-summary-round-01.md`

## Technical Plan

1. Add a synchronized accumulator for cross-tick danmaku perf samples and expose immutable summary snapshots.
2. Keep `/danmaku perf` for latest-frame output, then add `summary` and `reset` subcommands for rolling-window profiling.
3. Verify with `compileJava`, then record the round summary and move the active plan to the actual 90k profiling step.
