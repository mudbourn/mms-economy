# mms-economy

A standalone Fabric mod for Minecraft 1.21.11 (Java 21) that adds a two-tier gem
currency to MMSLive01: a virtual per-player wallet, physical Pice and Colt gems,
a bank, transaction taxes into a server treasury, player-to-player payments and
deals, a barrel-backed marketplace, and a transaction-history ledger.

## Currency

- Wallet: one `long` per player counted in Pice, the minor unit and currency of
  record. Displays as `Colt.Pice`, for example `12.34 Colt`.
- Denomination: 100 Pice make 1 Colt (configurable). Colt is the headline unit.
- Physical gems: `pice` (worth 1) and `colt` (worth 100), plus their storage
  blocks, are deposit and withdraw tokens, not the currency itself.

## Commands

- `/pay <player> <amount>`: taxed transfer between online players.
- `/deal <player>`, `/deal offer <amount>`, `/deal confirm`, `/deal cancel`,
  `/deal info`: a dual-confirm currency trade with a timeout.
- `/eco balance [player]`, `/eco deposit|withdraw <amount>` (at a bank),
  `/eco history`, `/eco market [buy <index>]`, `/eco shop list <price>|unlist
  <index>`, `/eco sell <count>` (server-sell at a bank).
- Admin: `/eco give|take <player> <amount>`, `/eco treasury balance|pay|take`,
  `/eco config [key [value]]`, `/eco buylist reload`.

## Build

```bash
./gradlew build
```

`./gradlew build` is the correctness gate; `runClient` and `runServer` launch a
dev instance under `run/`. Versions are pinned in `gradle.properties`.

## Attribution

Gem textures (Pice from carnelian, Colt from citrine, and the block variants) are
from Silent's Gems by SilentChaos512, MIT licensed
(https://github.com/SilentChaos512/SilentGems), reused under their own ids. The
bank block textures and the bank sound cues are original work by the mod author.
Wallet and history storage use Cardinal Components
(https://github.com/Ladysnake/Cardinal-Components-API).
