# AntiXray

Heuristic X-Ray detection with packet phantom ore traps and StorageESP/BaseFinder masking for Folia `26.2+`.

## Compatibility

| Parameter | Value |
| --- | --- |
| Core | Folia, Paper (`api-version: '26.2'`, `folia-supported: true`) |
| Java | `25+` |
| Required | `packetevents` |

## Build

```powershell
.\gradlew.bat shadowJar
```

Output JAR: `build/libs/AntiXray-1.0.jar`.

## Commands

Main command: `/ax` (aliases: `/antixray`, `/axr`).

| Command | Action | Permission |
| --- | --- | --- |
| `/ax reload` | Reload `config.yml`, `basefinder.yml`, `message.yml` | `antixray.admin` |
| `/ax alerts` | Toggle staff X-Ray alerts | `antixray.alerts` |
| `/ax check <player>` | Show player suspicion and trap counters | `antixray.check` |
| `/ax spawn <player> [ore]` | Spawn a phantom ore vein near player | `antixray.spawn` |
| `/ax reset <player>` | Reset player profile and active traps | `antixray.admin` |
| `/ax history <player> [page]` | View SQLite detection history | `antixray.check` |
| `/ax update` | Download latest GitHub release JAR | `antixray.update` / `antixray.admin` |

## Permissions

| Permission | Purpose | Default |
| --- | --- | --- |
| `antixray.admin` | Admin commands and reset | op |
| `antixray.alerts` | Receive and toggle alerts | op |
| `antixray.check` | Run `/ax check` and `/ax history` | op |
| `antixray.spawn` | Run `/ax spawn` | op |
| `antixray.bypass` | Bypass X-Ray checks | false |
| `antixray.storage.bypass` | Bypass StorageESP/BaseFinder masking | false |
| `antixray.storage.view` | Spectator bypass for hidden storages | op |
| `antixray.update` | Update notifications and `/ax update` | op |

## Configuration

- `config.yml` — suspicion heuristics (`sharp-turn-to-ore`, `rapid-consecutive-veins`, `ore-ratio-window`, `darkness-mining`, `reaction-to-ore`), decay, cave filter, phantom traps, punishments, per-world ore limits, SQLite `data.db`
- `basefinder.yml` — packet-level hiding of buried containers and spawners; see [BaseFinder module](basefinder.md)
- `message.yml` — alert templates and command output

[Русская документация](READMERU.md)
