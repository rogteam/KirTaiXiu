# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

**KirTaiXiu** is a Paper 1.21 Minecraft plugin implementing a Vietnamese Tài Xỉu (Sic Bo) dice gambling minigame. Players bet real in-game currency (via Vault) on outcomes of 3 dice. The plugin runs autonomously — rounds cycle on a timer with no admin intervention required.

**Hard dependency**: Vault must be present. The plugin disables itself if Vault economy is unavailable.

## Build commands

```bash
./gradlew build          # compile + test + jar → build/libs/KirTaiXiu-1.0.2.jar
./gradlew test           # run unit tests only
./gradlew jar            # jar only, skip tests
```

Java 21 toolchain is required. The output jar goes to `build/libs/` and must be copied manually to the server's `plugins/` folder.

To run a single test class:
```bash
./gradlew test --tests "kir.taixiu.TaiXiuRulesTest"
```

## Architecture

### Class responsibilities

| Class | Role |
|---|---|
| `KirTaiXiuPlugin` | Main plugin class + Bukkit `Listener`. Owns all game state and Bukkit API calls. |
| `TaiXiuRules` | Pure stateless logic — dice validation, win checks, money parsing, history formatting. No Bukkit imports. |
| `TxBetType` | Enum for the 5 bet types: `TAI_XIU`, `EVEN_ODD`, `TOTAL`, `TRIPLE_ANY`, `TRIPLE_EXACT`. |

`TaiXiuRules` is the only fully unit-testable class. All `KirTaiXiuPlugin` logic that doesn't need Bukkit belongs there.

### Round lifecycle

A `BukkitTask` fires every 20 ticks (1 second). State machine:

1. **Betting open** (`bettingOpen=true`, `resultPhase=false`): countdown from `round-seconds` (default 180s). BossBar shows countdown.
2. **Settle** (`secondsLeft <= 0`): dice are rolled, bets paid out, jackpot checked, history entry written, `data.yml` saved. Transitions to result phase.
3. **Result display** (`resultPhase=true`): countdown from `result-display-seconds` (default 8s). BossBar shows dice result.
4. Back to step 1 — `bets` list is cleared, new round opens.

### Economy flow

- On `placeBet`: `economy.withdrawPlayer(amount)` immediately. A `house-fee-percent` (3% default) is deducted from the bet. Half of that fee (`jackpot-share-of-fee-percent`) feeds the jackpot.
- On win: `economy.depositPlayer(amount * multiplier)`. Multiplier comes from `config.yml` payouts section.
- Jackpot: triggered when all 3 dice are equal AND the dice value is in `jackpot.trigger-triples` AND the player bet `TRIPLE_EXACT` on that exact value. Split equally among all qualifying winners.

### Persistence (`data.yml`)

Written on `onDisable` and after every `settleRound`. Stores:
- `jackpot` — current jackpot pool (persists across restarts)
- `history` — list of round result strings, capped at `history.keep-last`
- `stats.<uuid>` — per-player: name, wagered, profit, wins, losses

### GUI

A 45-slot chest inventory opened with `/tx` (no args). It is informational only — clicking slots either shows a chat message or displays history. Actual bets are placed via commands only. Slot layout is hardcoded by index in `openGui()`.

### Commands & permissions

- `/taixiu` (alias `/tx`) — main command
- `kirtaixiu.use` — default `true`, required to place bets
- `kirtaixiu.admin` — default `op`, required for `/tx reload` and `/tx debug`

### `parseMoney` shorthand

`TaiXiuRules.parseMoney()` accepts `k` (×1,000), `m` (×1,000,000), `b` (×1,000,000,000) suffixes and comma-separated numbers. All monetary input from players goes through this method.

### Message system

All player-facing strings come from `config.yml` under `messages.*`. Fallback defaults are hardcoded in `defaultMessage()`. Color codes use `&` prefix, translated via `ChatColor.translateAlternateColorCodes`. The prefix (`general.prefix`) is prepended to every message via `msg()`.
