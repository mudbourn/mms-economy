# mms-economy

A standalone Fabric mod for Minecraft 1.21.11 (Java 21) that adds a two-tier gem
currency to MMSLive01: a virtual per-player wallet, physical Pice and Colt gems,
a bank, transaction taxes into a server treasury, player-to-player payments and
wholesale deals, a barrel-backed marketplace, and a transaction-history ledger.

## Currency

- Wallet: one `long` per player counted in Pice, the minor unit and currency of
  record. Displays as `Colt.Pice`, for example `12.34 Colt`.
- Denomination: 100 Pice make 1 Colt (configurable). Colt is the headline unit.
- Physical gems: `pice` (worth 1) and `colt` (worth 100), plus their storage
  blocks, are deposit and withdraw tokens, not the currency itself.

## Commands

- `/pay <player> <amount>`: taxed transfer between online players.
- `/deal offers`, `/deal list`, `/deal add <lotSize> <price>` (held item),
  `/deal remove <id>`, `/deal order <offerId> <lots>`, `/deal accept|decline|cancel <id>`:
  wholesale trade between depot owners. The Deals tab of the depot menu does
  the same and also takes requests against regular shop listings. The buyer's
  money is escrowed until the seller accepts, the goods travel until the next
  in-game day, then land in the buyer's depot and the seller is paid.
- `/eco balance [player]`, `/eco deposit|withdraw <amount>` (at a bank),
  `/eco history`, `/eco market [buy <index>]`, `/eco shop list <price>|unlist
  <index>`, `/eco sell <count>` (server-sell at a bank).
- `/bill create <player> <amount> <intervalDays> <lateRatePercent> <label>`:
  ask a player to pay you a recurring bill; they start it with `/bill accept
  <id>`. `/bill pay <player> <amount> <intervalDays> [late <percent>] <label>`
  starts a recurring payment from you (a salary) at once. `/bill list` shows
  bills and debts, `/bill cancel <id>` ends a bill (debt stays owed).
- `/bond offer <lender> <principal> <ratePercent> <couponDays> <termDays>
  <lateRatePercent>`: ask for a loan; the lender uses `/bond accept` and
  receives a bearer bond item. Right-click a bond to collect its accrued
  coupons and principal. `/bond market` and `/bond buy <offering>` buy
  Treasury bonds.
- Admin: `/eco bond issue <principal> <ratePercent> <couponDays> <termDays>
  <count>`, `/eco bond list`, `/eco bond cancel <offering>`.
- Admin: `/eco give|take <player> <amount>`, `/eco treasury balance|pay|take`,
  `/eco config [key [value]]`, `/eco buylist reload`.

## Bills and bonds

Bills and bonds run on the in-game day clock. A charge that cannot be paid
becomes a debt that grows by its late rate each day and is collected from the
payer's wallet once a day and on join. Bills are taxed at `taxBillBps`; only the
interest of a bond redemption is taxed, at `taxBondBps`. Bond terms live on the
server, so copied bond items cannot be redeemed twice.

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
