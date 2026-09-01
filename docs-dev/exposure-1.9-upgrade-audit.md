# Exposure 1.9 Upgrade Audit

Audit date: 2026-09-01

The development dependency is now Exposure 1.9.21 for Minecraft 1.20.1 Forge,
published as a beta on 2026-06-09 through Modrinth Maven. This is not a drop-in
dependency update from the former Exposure 1.7 line (CurseForge file `5545255`).

## Confirmed constraints

- Exposure 1.9.13 warns that upgrading from the 1.7 data format removes most
  existing Exposure world data. A pack update therefore needs an explicit
  migration/backup decision.
- `ModifyFrameDataEvent` was removed, but Exposure 1.9 provides
  `ModifyFrameExtraDataEvent` before frame serialization. The compatibility
  layer now uses it to attach danmaku statistics and cache the exact erase
  candidates, then uses `FrameAddedEvent` to commit the erase transaction.
- Exposure 1.9 also provides `ModifyEntityInFrameDataEvent`; the compatibility
  layer uses it to preserve the Youkai model id in the serialized entity data.
- The event and item APIs changed shape and package:
  `FrameAddedEvent` now exposes a `CameraHolder` and `Frame`; `CameraItem` moved
  to `world.item.camera`; the photograph renderer moved and changed from static
  helpers to an instance API.
- The 1.9.21 Forge jar contains `exposure-common.mixins.json` and
  `exposure-forge.mixins.json`, but contains no refmap and neither mixin config
  declares a `refmap`. This is an upstream packaging limitation. The published
  jar is left untouched; userdev generates a separate patched copy with a
  generated refmap and places only that copy on the development runtime classpath.
  Do not add a global refmap/remapping override for a third-party mixin config.

## Upgrade direction

The 0.25.5 adapter uses the 1.9 `Frame` record and `CameraHolder` APIs. The
client overlay receives the serialized frame in the existing YH network packet
and renders it through Exposure's instance `PhotographRenderer`.

Exposure 1.9.13+ warns that upgrading from the 1.7 data format removes most
existing Exposure world data. Pack authors should back up the world before
upgrading; no automatic migration is provided by this fork.
