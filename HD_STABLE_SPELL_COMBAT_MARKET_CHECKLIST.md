# HD Stable Spell, Combat, and Market Change Checklist

## Baseline and scope

- Target repository: `D:\IdeaProjects\Youkai-Homecoming-hd-stable`
- Target branch baseline: `hd-stable` at `654a298ff` (`docs: add hd-stable maintenance workflow`)
- The baseline is a descendant of the old workspace committed HEAD `5e468a871`. Do not re-port already committed spell/runtime/market work from the old repository.
- Follow `docs-dev/hd-stable-workflow.md`: create a short-lived feature branch from `hd-stable`, preserve upstream 2.7.0 changes, verify there, and merge with `--no-ff` only after review.
- The old workspace has uncommitted files that are not present in this repository. Their required handling is listed in the final section.

## P0: Review boundaries before implementation

- [ ] Confirm the new work starts from the clean `hd-stable` worktree and does not overwrite the upstream food, fluid, worldgen, entity, language, or build changes added after `5e468a871`.
- [ ] Keep built-in spell definitions authoritative. Market synchronization and custom-spell deletion must never overwrite or delete an ID for which `SpellRegistry.hasDefault(id)` is true.
- [ ] Define three distinct spell origins before adding bulk cleanup: built-in/KJS default, player/world custom, and market-managed import. Do not use only `hasDefault == false` as the market-import test.
- [ ] Treat all downloaded market JSON as untrusted server input.

## P1: Fix Small Fairy spell execution

### Current defect

- `SmallFairy.shouldShowSpellCircle()` always returns `false` to hide its spell circle.
- `YoukaiEntity.aiStep()` currently requires `shouldShowSpellCircle()` before ticking both `spellRuntime` and legacy `spellCard`.
- Consequently, a small fairy resets its spell every tick and cannot fire either its default legacy spell or a spell assigned by `/yhspell set`.

### Required change

- [ ] Separate spell execution eligibility from spell-circle visibility in `YoukaiEntity`.
- [ ] Add a dedicated method such as `shouldTickSpell()`/`canCastSpell()` and use it for both runtime and legacy spell paths.
- [ ] Preserve existing special behavior such as Rumia's blocked/charged casting restrictions.
- [ ] Override the new casting predicate for `SmallFairy` so it may cast while it has a valid target, while `shouldShowSpellCircle()` remains `false`.
- [ ] Do not special-case `SmallFairy` with an `instanceof` inside the base tick loop.

### Acceptance checks

- [ ] Naturally spawned small fairies fire their default `fairy:*` spell when they acquire a target.
- [ ] `/yhspell set <small_fairy> <spell_id>` runs the assigned data-driven spell once the fairy has a target.
- [ ] No spell circle is rendered for small fairies.
- [ ] Rumia and other entities that intentionally suspend casting still behave as before.

## P1: Make STG combat state, resources, HUD, and defeat agree

### Current defect

- Full youkaified/fairy players may enter `performDanmakuHit()` without a youkai session, PvP opponent, or forced-combat flag.
- `getInfoLines()` only shows the full STG resource HUD for an explicit session/opponent/forced state.
- Raw `DanmakuItem` and `LaserItem` PvP hits do not establish `playerOpponents`; only selected spell-item paths call `GrazeHelper.addSession()`.
- `performDanmakuHit()` can therefore mutate uninitialized or stale resources while no STG GUI is visible.
- The last-hit event is currently posted only when the source is a `YoukaiEntity`. Player and other living sources can reach last life without the same defeat hook.
- There is no built-in, clearly visible defeat result UI; the default path mainly clears combat state and applies weakness.

### Required state model

- [ ] Treat youkaified/fairy status as eligibility to enter STG combat, not as proof that an STG combat context already exists.
- [ ] Make resource absorption require an explicit combat context: matching youkai session, matching PvP opponent, or forced STG state.
- [ ] Before the first legitimate hostile STG hit, establish the context and call `initStatus()` before any bomb/life/power mutation.
- [ ] For hostile player danmaku and laser hits, establish reciprocal `playerOpponents` when the hit should start STG PvP.
- [ ] For a youkai actively targeting an eligible player, establish the matching youkai session before resolving the first STG hit.
- [ ] Remove the unconditional `isFullCharacter()` fallback from `shouldAbsorbDanmakuFrom()` once context creation is handled centrally.
- [ ] Add a defensive guard in `performDanmakuHit()` so it cannot spend STG resources outside a valid initialized context.
- [ ] Keep normal health damage for stray/non-STG danmaku when no legitimate combat context should be created.

### Defeat handling

- [ ] Post the last-hit event for every valid `LivingEntity` source, not only `YoukaiEntity`, or replace it with a general STG defeat event that retains attacker/source information.
- [ ] Define and implement visible defeat feedback: packet plus overlay/message, sound/effect, or another explicit result agreed during review.
- [ ] Ensure defeat stops relevant spell runtimes, erases hostile active danmaku, clears sessions/opponents, synchronizes the client, and cannot leave an invisible resource state behind.

### Acceptance matrix

- [ ] Full youkaified/fairy player hit by a hostile youkai: resources initialize, full HUD appears, then the hit is resolved.
- [ ] Full youkaified/fairy player hit by raw player danmaku or laser: reciprocal PvP state and both relevant HUDs appear.
- [ ] Non-transformed player hit by a stray projectile outside explicit STG combat: normal health damage, no hidden resource loss.
- [ ] Last life from youkai and player sources produces the same observable defeat lifecycle.
- [ ] Leaving/dying/changing dimension removes stale sessions, opponent bars, active spells, and projectiles.

## P1: Remove the Rose danmaku type completely

No compatibility fallback is required. No built-in spell currently uses this bullet type.

- [ ] Remove `ROSE` from `YHDanmaku.Bullet`.
- [ ] Remove the `case ROSE` render branch and its now-unused import from `DanmakuItem`.
- [ ] Remove Rose from spell-editor bullet options/localization, including the `SpellEditorLocalization` mapping.
- [ ] Delete all Rose bullet textures under:
  - `src/main/resources/assets/youkaishomecoming/textures/entities/bullet/rose/`
  - `src/main/resources/assets/youkaishomecoming/textures/item/bullet/rose/`
- [ ] Regenerate/clean generated item models, tags, language entries, and other datagen output so no `rose_danmaku` registry asset remains.
- [ ] Update `COLOR_WHEEL_TEST.md` by removing Rose from the FIXED-mode list rather than carrying over the old uncommitted wording change.
- [ ] After removal, search source/resources for `Bullet.ROSE`, `case ROSE`, `rose_danmaku`, and Rose bullet asset paths. Rose-bush food recipes are unrelated and must remain.
- [ ] Review whether `AnimatedProjectileType` is dead after Rose removal. Remove the renderer/cache/preview support only if no remaining feature constructs it; otherwise keep the generic infrastructure.

## P2: Move the market command under `/yhspell`

### Current state

- The market registers client commands `/yhmarket` and `/spellmarket`.
- The server `/yhspell` root currently requires permission level 2, while opening the market should remain available to ordinary players.

### Required change

- [ ] Add `/yhspell market` as the supported player-facing command.
- [ ] Restructure `/yhspell` permissions so `market` is available to normal players while editing, setting, resetting, deleting, synchronizing, and debugging commands retain their existing operator requirements.
- [ ] Open the client market GUI through a server-to-client packet or another dedicated-safe path; do not reference client-only screen classes from common/server code.
- [ ] Remove `/yhmarket` and `/spellmarket`, unless review explicitly chooses a one-release deprecated alias. The final documented command must be `/yhspell market`.
- [ ] Consider operator subcommands under the same tree: `/yhspell market sync <tag>`, `status`, and `prune <tag>`.

## P2: Add server-side market synchronization and KubeJS APIs

### Architecture

- [ ] Split the current client-only market HTTP implementation into a common asynchronous HTTP client plus separate client and server managers.
- [ ] Move server market URL/enabled/polling settings out of client config into server/common config.
- [ ] Keep GUI-only functions such as likes, comments, fingerprinting, and screens in the client manager.
- [ ] Add exact tag filtering. Prefer a backend `tag=<exact>` and `updated_since`/cursor API; otherwise verify `SpellListEntry.tags` exactly after fetching all relevant pages.
- [ ] Never block the Minecraft server thread on HTTP. Download and parse off-thread, then schedule registry/storage mutations on the server thread.
- [ ] Stage the complete new result, validate every spell, and atomically replace the managed tag pool. If fetching or validation fails, retain the previous working pool.

### Provenance and collision handling

- [ ] Persist a market import manifest containing at least market UUID, local spell ID, exact tags, upload/update timestamp, content hash, and import time.
- [ ] Do not overwrite built-ins, KJS defaults, or unrelated player/world custom spells.
- [ ] Define a stable collision policy for market spell IDs. Reject collisions or perform a complete, validated ID/phase/reference remap; do not silently overwrite by `ResourceLocation`.
- [ ] Pruning a tag must delete only entries owned by that managed tag/pool.

### Security and resource budgets

- [ ] Fix the existing market import path so validation is actually called. `validateMarketImport()` currently exists but `importMarketSpell()` does not invoke it.
- [ ] Reject `run_command` for automatically imported market content.
- [ ] Review and restrict indirect execution/reference actions such as `force_spell` and `fire_spell`.
- [ ] Enforce server-side limits for JSON bytes, phase count, action count, nesting depth, repeat/burst count, bullet count, shooter count, lifetime, expression length, and other amplification paths.
- [ ] Use HTTPS for automatic server imports and verify a content hash; add signatures if the market service can provide them.
- [ ] Cap the number of imported spells per tag and the synchronization frequency.

### KubeJS surface

- [ ] Add a dedicated binding such as `YHSpellMarket` rather than exposing raw HTTP classes.
- [ ] Provide an async/job-based `syncTag(server, tag, options)` API with a structured result: added, updated, unchanged, removed, rejected, and errors.
- [ ] Provide `listByTag(tag)`, `randomByTag(tag)`, and origin/metadata lookup methods for gameplay scripts.
- [ ] Add completion/failure events such as `YHSpellEvents.marketSyncCompleted` so scripts can refresh their active spell pools without polling Java futures directly.
- [ ] Add explicit runtime helpers to apply/set a spell on an entity, fire a temporary spell/proxy, and stop it with optional projectile erasure.
- [ ] Add safe unload/delete APIs for market-managed imports.
- [ ] If a general non-built-in deletion API is also exposed, name it as a destructive operation, require an explicit flag/options object, protect `hasDefault` entries, and define whether it stops active runtimes, erases existing danmaku, removes world/global files, and synchronizes clients.
- [ ] Deleting a registry definition must not leave an existing `SpellRuntime` or already spawned projectile running unintentionally.

### Automation acceptance checks

- [ ] A KJS scheduler can sync a configured tag on server start and periodically afterward.
- [ ] A newly published matching market spell becomes available to subsequent scripted battles without a server restart.
- [ ] Updated market content is revalidated and safely reapplied according to explicit policy.
- [ ] Content removed from the managed tag is pruned without touching player-created custom spells.
- [ ] Network/market failure leaves the last valid pool playable.
- [ ] Malicious/oversized content is rejected with actionable logs and a structured KJS result.

## P3: Migrate or intentionally resolve old-workspace uncommitted files

Old workspace: `D:\IdeaProjects\Youkai-Homecoming`

### Tracked modifications

- [ ] `.gitignore`: port the `# Code Mind local runtime` entry and `.codemind/runtime/` ignore rule if Code Mind remains part of the workflow.
- [ ] `gradle.properties`: do not copy the old full version string. Keep the HD baseline version and port only the intended HCDRS update, resulting in `ll_version = 2.7.0+HCDRS_0.17.4` unless dependency review selects a newer compatible value.
- [ ] `COLOR_WHEEL_TEST.md`: do not copy the old change describing Rose as a static rotating texture. Rose is now being removed; update the document accordingly.
- [ ] `DanmakuItem.java`: do not copy the old Rose `RotatingProjectileType` patch. It is superseded by complete Rose removal. Preserve any unrelated local edits if later discovered.

### Untracked files/directories

- [ ] `.codemind/`: not transferred. Review `agent-protocol.md`, `layout.json`, `project.json`, and `semantic.json` as local tooling metadata. Migrate only intentionally useful project metadata; do not blindly commit snapshots or backup copies.
- [ ] `.codemind/snapshots/20260713-160020Z-r1-yh/`: local snapshot data; archive or ignore intentionally rather than treating it as product source.
- [ ] `.codemind/*.bak-*`: local backup files; normally exclude from the repository.
- [ ] `.gitignore.bak-3d943770-2c6a-4412-8d88-0d5499ec6bf8`: backup of the pre-Code-Mind `.gitignore`; do not copy as a product file. Retain externally only if needed for audit.

## Verification and review gates

- [ ] Run `git diff --check` before compilation.
- [ ] Run `./gradlew.bat organizeLang --no-daemon` and review generated language changes rather than editing only flattened runtime JSON.
- [ ] Run the repository's applicable datagen task and confirm Rose registry assets are removed without unrelated generated churn.
- [ ] Run `./gradlew.bat compileJava --no-daemon --console=plain`.
- [ ] Add focused tests or deterministic test hooks for spell ticking predicates, STG context transitions, market import validation, provenance-aware pruning, and destructive deletion protection.
- [ ] Manually test dedicated-server behavior. Client-only checks are insufficient for KJS market synchronization and `/yhspell market` packet handling.
- [ ] Keep commits separated by concern where practical: small-fairy fix, STG lifecycle, Rose removal, command migration, server market/KJS API, and tooling/version migration.


