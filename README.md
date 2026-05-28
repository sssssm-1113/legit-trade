# LegitTrade

A Fabric 1.20.1 trade mod. Place `trade_block` to open trade GUI, exchange low-value items for high-value items with XP rewards.

[中文文档](doc/README-cn.md)

## Features

- **In-game Trade GUI** - Right-click `trade_block` to open
- **Grouped Trades** - Trades organized by groups for easy management
- **NBT Support** - Match and output items with NBT data
- **Web Config UI** - Manage trades via browser
- **Auto-fill** - Automatically fill input slots from inventory
- **Batch Trading** - Shift+click output slot for multiple trades
- **Hot Reload** - `/tradereload` reloads config and syncs to online players

## Build

```bash
./gradlew build
```

Output: `build/libs/legittrade-1.0.2[-SNAPSHOT-date].jar`

Release build:
```bash
./gradlew build -Prelease
```

## Usage

### In-game

1. Place `trade_block` and right-click to open trade GUI
2. Left panel shows grouped trade list
3. Click a trade, system auto-fills input slots from inventory
4. When input meets requirements, output preview shows on right
5. Click output slot to execute trade
6. Shift+click output slot for batch execution (until inventory full or input exhausted)

### Commands

- `/tradereload` - Server-side hot reload, syncs to online players (requires OP)

### Web Config UI

After server starts, visit `http://localhost:39482` (or configure `bindAddress` for remote access):

- View all trade configs
- Add/edit/delete trades
- Search item IDs
- NBT editor
- Auto-sync to online players after save

![Trade List](doc/img/brief.png)

![Trade Edit](doc/img/edit.png)

![NBT Editor](doc/img/nbt-edit.png)

Config file: `config/legittrade-web.json`

```json
{
  "enabled": true,
  "port": 39482,
  "bindAddress": "127.0.0.1"
}
```

## Configuration

Path: `config/legittrade.json`

### Grouped Format (Recommended)

```json
[
  {
    "group": "Building",
    "trades": [
      {
        "input": "minecraft:dirt",
        "output": "minecraft:stone",
        "inputCount": 64,
        "outputCount": 1,
        "xpReward": 10
      }
    ]
  }
]
```

### NBT Support

```json
{
  "input": "minecraft:diamond_sword",
  "output": "minecraft:diamond_sword",
  "inputNbt": "{Damage:0}",
  "outputNbt": "{Enchantments:[{id:\"minecraft:sharpness\",lvl:5}]}",
  "nbtMatchMode": "exact",
  "inputCount": 1,
  "outputCount": 1,
  "xpReward": 100
}
```

### Field Reference

| Field | Type | Description |
|-------|------|-------------|
| `group` | string | Group name |
| `input` | string | Input item ID |
| `output` | string | Output item ID |
| `inputNbt` | string? | Input item NBT match condition |
| `outputNbt` | string? | Output item NBT |
| `nbtMatchMode` | string | NBT match mode: `exact`/`contains`/`ignore` |
| `inputCount` | int | Consume amount, range 1~64 |
| `outputCount` | int | Receive amount, range 1~64 |
| `xpReward` | int | XP reward, ≥ 0 |

### NBT Match Modes

- `exact` - Exact NBT match (default)
- `contains` - Input item contains specified NBT tags
- `ignore` - Ignore input item NBT

### Config Validation

On load, automatically:

- Filter invalid item IDs
- Validate NBT syntax
- Limit count range 1~64
- Deduplicate trades
- Fallback to defaults for empty config

### Limits

- Max groups: 128
- Max trades per group: 1024
- Max item ID length: 128 chars
- Max group name length: 64 chars
- Max NBT length: 4096 chars

## Technical Details

- Trade execution handled server-side, prevents client spoofing
- Auto-sync trade config on player join
- Auto-sync to online players after config update
- Clear local cache on client disconnect
- `trade_block` has complete resource chain (recipe, model, lang files, loot table)

## Dependencies

| Dependency | Version |
|------------|---------|
| Minecraft | 1.20.1 |
| Fabric Loader | 0.14.23 |
| Fabric API | 0.90.4+1.20.1 |
| Java | 17 |

## License

MIT
