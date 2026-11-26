# BaseFinder & StorageESP Protection

Packet-level masking module configured in `plugins/AntiXray/basefinder.yml`.

## How it works

1. During chunk packet transmission (`CHUNK_DATA`), AntiXray checks each target block in `hidden-blocks` (`CHEST`, `BARREL`, `SPAWNER`, `SHULKER_BOX`, etc.).
2. If all 6 neighboring blocks are solid occluding blocks (`OccTbl`), the block and its tile entity are masked using the most common neighboring camouflage block (`use-neighbor-camouflage`) or world fallback (`above-replacement` / `below-replacement`).
3. `BLOCK_ENTITY_DATA`, `BLOCK_ACTION`, `BLOCK_CHANGE`, and `MULTI_BLOCK_CHANGE` packets for masked coordinates are suppressed until revealed.

## Reveal triggers

Masked blocks are dynamically revealed (with tile entity NBT and double-chest partner) on:
- start digging (`on-start-dig`)
- neighboring block break (`on-block-break`)
- explosions (`on-explosion`), pistons (`on-piston`), fire burn (`on-block-burn`), or player interaction (`on-interact`)

## Bypass

- `antixray.storage.bypass` when `bypass.permission-enabled: true`
- Creative mode (`bypass.ignore-creative`)
- Spectator mode (`bypass.ignore-spectator`) or moderators with `antixray.storage.view` (`bypass.ignore-spectator-moderators`)

[Русская версия](basefinderru.md)
