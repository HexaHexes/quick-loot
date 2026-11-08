# Quick: Loot!

Quick: Loot! puts mob and block drops, and the experience they carry, straight
into your inventory instead of leaving them on the ground. You walk past a kill
and the loot is already yours, and a mined vein fills your slots while the
experience bar fills with no orb in sight.

Carried Sophisticated Backpacks can claim loot through their pickup upgrades,
loot containers get a one-click deposit button, party kills can be shared or
rotated, and with Lightman's Currency installed your coins go into the
equipped wallet rather than clogging the inventory.

The mod is server-authoritative: players do not need it installed to join or
to receive any of the gameplay - a server running Quick: Loot! inserts loot,
awards experience and shares party kills for everyone connected. Only the
quick-insert button and the Loot Journal pickup notifications need the mod on
the client.

## What it does

- **Drops land in your inventory.** Mob death drops and block break drops are
  inserted into the killer's or breaker's 36 main slots the moment they would
  have spawned, merging into existing stacks first and then filling empty ones.
  Anything that genuinely does not fit stays in the world.
- **Backpacks take what their pickup upgrades allow.** With Sophisticated
  Backpacks installed, loot runs through each carried backpack's pickup chain
  before your inventory, the same path it takes when you walk over an item. A
  Pickup, Advanced Pickup, Magnet or Advanced Magnet upgrade decides what its
  backpack claims (its filter and settings apply), a backpack without one of
  those upgrades claims nothing, and whatever is left fills your slots and
  then spills into the world.
- **Experience comes with the loot.** Mob and block experience is awarded
  directly instead of spawning orbs, using the same rules as picking an orb up,
  mending repairs first and all. Loot Journal still shows the pickup
  notification, and turning the config gates off restores vanilla drops and
  orbs completely.
- **A quick-insert button on loot containers.** Chests, shulker boxes, hoppers
  and dispensers the server has actually seen loot in get a button that empties
  them into your inventory in one click, and only for stacks you already carry.
  A config scope widens it to any container if you want that.
- **Party loot.** Kills shared with a party can go to the killing blow (the
  default), rotate one member per kill, or split every kill, either as a full
  copy per member or divided evenly with remainders to the primary. Block drops
  and experience are never shared.
- **Coins go to your wallet.** With Lightman's Currency installed, coin stacks
  on every insert path are credited to your equipped wallet first, with
  inventory and backpacks as the fallback when no wallet is worn. Loot Journal
  shows a pickup toast for any currency entering a wallet during play, exactly
  once per insert, including Lightman's Currency's own auto-pickup and
  wallet-screen deposits.

## Party loot

Who receives a kill's loot is the rule and how much each recipient gets is the
amount, both set in server config (`party.lootRule`, `party.lootAmount`). At
the defaults the feature is inert: the killer keeps everything, exactly like
solo play.

| Rule \ Amount | `OWNER_ONLY` (default) | `FULL_TO_ALL` | `SPLIT_EQUALLY` |
|---|---|---|---|
| `KILLING_BLOW` (default), killer only | the killer gets the whole stack | the killer gets the whole stack (the only recipient) | the killer gets the whole stack (the only recipient) |
| `ROUND_ROBIN`, one rotating member per kill | the rotated member gets the whole stack | the rotated member gets the whole stack (the only recipient) | the rotated member gets the whole stack (the only recipient) |
| `FREE_FOR_ALL`, every eligible member | the killer (primary) gets the whole stack and the rest get nothing | every member receives a full copy of each stack | each stack divided evenly, remainders one each in stable order with the killer (primary) first |

Recipients are limited to party members who are online and in the killed mob's
dimension, so round robin rotates past the rest. Rotation state persists per
party in the world data, and each recipient's share rides the normal insert
chain (inventory, then backpacks), with any true remainder spawning at the
death site. Block drops and block experience are never party-shared.
Membership comes from Better Party, Open Parties and Claims or FTB Teams;
`party.source=AUTO` tries them in that order.

## Settings

Installed and enabled there is nothing to do first: the defaults behave like
vanilla plus straight insertion. Every toggle is a config value, editable
in-game with Configured installed.

| Key | Default | What it does |
|---|---|---|
| `drops.enabled` | `true` | Master switch for all Quick: Loot! behaviour |
| `drops.mobDrops` / `drops.blockDrops` | `true` | Insert mob and block drops (experience has its own keys below) |
| `drops.mobExperience` / `drops.blockExperience` | `true` | Award kill and mining experience directly instead of spawning orbs; each independent of its drop key |
| `drops.lightmansCoins` | `true` | Collect Lightman's Currency coins spawned at a mob's death into the insert pipeline |
| `drops.useBackpacks` | `true` | Spill overflow into carried Sophisticated Backpacks |
| `drops.coinsToWallet` | `true` | Route Lightman's Currency coins into the equipped wallet on every insert path |
| `drops.lootJournalNotify` | `true` | Send inserted stacks and awarded experience to Loot Journal |
| `quickDeposit.enabled` | `true` | Allow the quick-insert button; the server validates every click |
| `quickDeposit.scope` | `LOOT_ONLY` | `LOOT_ONLY` shows the button only on containers the server has seen loot in, `ALL_CONTAINERS` on any whitelisted container |
| `quickDeposit.requireCarried` | `true` | Only deposit a stack the player already carries (inventory or backpack) |
| `quickDeposit.menus` | `chest, shulker, hopper, dispenser` | Which container screens offer the quick-insert button |
| `showQuickDepositButton` | `true` | Client-side display of the quick-insert button |
| `party.enabled` | `true` | Distribute kill loot among party members |
| `party.source` | `AUTO` | Party membership source; `AUTO` tries Better Party, then Open Parties and Claims, then FTB Teams |
| `party.lootRule` | `KILLING_BLOW` | Who receives loot (see the matrix above) |
| `party.lootAmount` | `OWNER_ONLY` | How much each recipient gets (see the matrix above) |

Turning `drops.coinsToWallet` off leaves coins as ordinary items, and setting
`party.lootRule` back to `KILLING_BLOW` returns every kill to solo behaviour.

## Dependencies

The mod runs on its own; each of these is optional and adds a piece:

| | Mod | Why |
|---|---|---|
| Optional | [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) | Loot routes into carried backpacks through their pickup upgrades |
| Optional | [Loot Journal](https://www.curseforge.com/minecraft/mc-mods/loot-journal) | Pickup notifications for inserted stacks and experience (client side) |
| Optional | [Lightman's Currency](https://www.curseforge.com/minecraft/mc-mods/lightmans-currency) | Coins route into the equipped wallet, with a pickup toast for every wallet insert |
| Optional | [Better Party](https://www.curseforge.com/minecraft/mc-mods/better-party), [Open Parties and Claims](https://modrinth.com/mod/open-parties-and-claims), [FTB Teams](https://www.curseforge.com/minecraft/mc-mods/ftb-teams) | Party membership for shared kill loot (tried in that order under `AUTO`) |

## Credits

- [NeoForge](https://neoforged.net/) — the mod platform.
- [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) by P3pp3r1y — backpack overflow through its public API.
- [Loot Journal](https://www.curseforge.com/minecraft/mc-mods/loot-journal) by Obscuria — pickup notifications through its public API.
- [Lightman's Currency](https://www.curseforge.com/minecraft/mc-mods/lightmans-currency) by Lightman314 — wallet and coin routing through its public API.
- [Better Party](https://www.curseforge.com/minecraft/mc-mods/better-party) — party membership through its public API.
- [Open Parties and Claims](https://modrinth.com/mod/open-parties-and-claims) by Xaero — party membership through its public API.
- [FTB Teams](https://www.curseforge.com/minecraft/mc-mods/ftb-teams) — party membership through its public API.

This mod embeds no third-party assets, textures, models or code.

## License

GNU Lesser General Public License v2.1 — see [`LICENSE`](LICENSE).
Copyright (c) 2026 Bitwise Madness.

## AI disclosure

Parts of this project were written with the assistance of AI coding tools.
AI-assisted code, data and documentation ship only after human review by the
maintainers.
