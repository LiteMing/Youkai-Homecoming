# Exposure 1.9 Upgrade Audit

Audit date: 2026-09-01

The current development dependency is the stable Exposure 1.7 line (CurseForge
file `5545255`). The newest Forge build for Minecraft 1.20.1 is Exposure 1.9.21,
published as a beta on 2026-06-09. It is not a drop-in dependency update.

## Confirmed blockers

- Exposure 1.9.13 warns that upgrading from the 1.7 data format removes most
  existing Exposure world data. A pack update therefore needs an explicit
  migration/backup decision.
- `ModifyFrameDataEvent` was removed. The current compatibility layer uses it
  before film serialization to attach danmaku statistics and cache the exact
  erase candidates. Exposure 1.9 only exposes `FrameAddedEvent`, which fires
  after `CameraItem.addFrameToFilm`.
- The event and item APIs changed shape and package:
  `FrameAddedEvent` now exposes a `CameraHolder` and `Frame`; `CameraItem` moved
  to `world.item.camera`; the photograph renderer moved and changed from static
  helpers to an instance API.
- The 1.9.21 Forge jar contains `exposure-common.mixins.json` and
  `exposure-forge.mixins.json`, but contains no refmap and neither mixin config
  declares a `refmap`. This matches the userdev remapping failure previously
  observed on this branch.

## Upgrade direction

Keep 1.7 as the default development/runtime dependency for 0.25.5. A 1.9 port
needs a dedicated compatibility adapter, a replacement pre-serialization hook
for frame extra data (or an upstream event), a rewritten client thumbnail path,
and a real Forge userdev launch test. Do not hide the missing refmap with a
global mixin setting: that would weaken diagnostics for this mod and other
dependencies.
