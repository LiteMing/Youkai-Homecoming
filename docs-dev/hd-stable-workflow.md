# hd-stable branch workflow

`hd-stable` is the maintained baseline that combines:

- `upstream/main`: upstream leisure, food, world, entity, and art updates.
- Local feature branches: danmaku, spell, combat, rendering, performance, and compatibility updates.

Do not develop features directly on `hd-stable`. Keep it buildable and use short-lived integration branches.

## Sync upstream

```powershell
git fetch upstream
git switch hd-stable
git switch -c sync/upstream-YYYYMMDD
git merge --no-ff upstream/main
```

Resolve conflicts and verify:

```powershell
.\gradlew.bat organizeLang --no-daemon
.\gradlew.bat compileJava --no-daemon --console=plain
```

Then merge the verified sync branch:

```powershell
git switch hd-stable
git merge --no-ff sync/upstream-YYYYMMDD
```

Track `upstream/main` for routine updates. Treat commits that exist only on `upstream/1.20/dev` as candidate patches until upstream merges them into `main`.

## Merge local work

Create local work from the current baseline:

```powershell
git switch hd-stable
git switch -c feat/topic-name
```

After review and verification, merge it back with a merge commit:

```powershell
git switch hd-stable
git merge --no-ff feat/topic-name
```

If upstream changed while a large local feature was in progress, merge the latest `hd-stable` into the feature branch before final review.

## Known conflict areas

- `build.gradle`: preserve local packaging and development dependencies while accepting upstream build fixes.
- `gradle.properties`: use the upstream release as the base version and retain the local HCDRS suffix.
- `BulkDataWriter.java`: preserve the safe VertexConsumer path and vanilla `putBulkData` bulk path.
- `YoukaisHomecoming.java`: retain both upstream registrations/reload listeners and local packet/compat registrations.
- Runtime language JSON: edit split files under `src/test/resources/youkaishomecoming/lang`, then regenerate. Do not maintain conflict resolutions only in the flattened runtime JSON.

Repository-local `git rerere` should remain enabled so repeated upstream conflicts can reuse reviewed resolutions.
