# Quick Loot

Mobs' and blocks' loot goes **straight into your inventory** instead of scattering on the
ground — with overflow spilling into your **Sophisticated Backpacks**, a **quick-insert
button** on loot containers, **party loot sharing**, experience awarded directly instead
of spawning orbs, and **Lightman's Currency coins routed into your wallet**.

## What it does

- **Kill and mine loot lands in your inventory.** Mob death drops and block break drops
  are inserted into the killer's / breaker's 36 main slots the moment they would have
  spawned — merge into existing stacks first, then empty slots. Whatever genuinely does
  not fit stays in the world; nothing is ever lost.
- **Backpack spill.** With [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks)
  installed, overflow continues into your carried backpacks through their filtered IO
  handler, so filter, void, slot-blocker and overflow upgrades all apply.
- **Experience comes with the loot.** Mob and block experience is awarded directly to the
  recipients instead of spawning orbs, using the exact same rules as picking an orb up —
  mending repairs first, and Loot Journal still shows the pickup notification. Gates off
  (`enabled` / `mobDrops` / `blockDrops`) restore vanilla orbs and drops completely.
- **Quick-insert button.** Loot containers ([Chest](https://minecraft.wiki/w/Chest),
  [Shulker Box](https://minecraft.wiki/w/Shulker_Box), [Hopper](https://minecraft.wiki/w/Hopper),
  [Dispenser](https://minecraft.wiki/w/Dispenser)) get a button that empties them into
  your inventory in one click. It only appears for containers the server has actually
  seen loot in (latched across reopens), and only for stacks you already carry — the
  scope config widens it to any container if you want that.
- **Party loot.** Kills shared with a party can go to the killing blow (default), rotate
  one member per kill, or split every kill — either as a full copy per member or divided
  evenly with remainders to the primary. Block drops and experience are never shared.
- **Coins go to your wallet.** With [Lightman's Currency](https://www.curseforge.com/minecraft/mc-mods/lightmans-currency)
  installed, coin stacks on every insert path — mob drops, block drops, party shares and
  quick-deposit — are credited to your equipped wallet first (server toggle,
  `drops.coinsToWallet`, default on). Inventory and backpacks remain the fallback when no
  wallet is worn or the wallet cannot take the coins, so nothing is ever lost. Loot
  Journal shows a pickup toast for **any** currency entering a wallet during play —
  Quick Loot's inserts, Lightman's Currency's own auto-pickup, wallet enchantments and
  wallet-screen deposits alike — exactly once per insert.

## Party loot

Who receives a kill's loot is the **rule**; how much each recipient gets is the
**amount**. Both are server config (`party.lootRule`, `party.lootAmount`), and the
feature is inert at the defaults — the killer keeps everything, exactly like solo play.

| Rule \ Amount | `OWNER_ONLY` (default) | `FULL_TO_ALL` | `SPLIT_EQUALLY` |
|---|---|---|---|
| `KILLING_BLOW` (default) — the killer only | killer gets the whole stack | killer gets the whole stack (only one recipient) | killer gets the whole stack (only one recipient) |
| `ROUND_ROBIN` — one rotating member per kill | the rotated member gets the whole stack | the rotated member gets the whole stack (only one recipient) | the rotated member gets the whole stack (only one recipient) |
| `FREE_FOR_ALL` — every eligible member | killer (primary) gets the whole stack; the rest get nothing | **every member receives a full copy** of each stack | each stack divided evenly; indivisible remainders go one each in stable order, **primary (killer) first** |

Notes: recipients are limited to party members who are **online and in the killed mob's
dimension** (round robin rotates past the rest), rotation state persists per party in
world data, and each recipient's share rides the normal insert chain — inventory,
then backpacks, with any true remainder spawning at the death site. Block drops and
block experience are never party-shared. Membership comes from Better Party, Open
Parties and Claims or FTB Teams (`party.source=AUTO` tries them in that order).

## Dependencies

| | Mod | Why |
|---|---|---|
| Required | Minecraft | — |
| Required | NeoForge | — |
| Optional | [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks) | Overflow spills into carried backpacks through their filtered IO handler |
| Optional | [Loot Journal](https://www.curseforge.com/minecraft/mc-mods/loot-journal) | Pickup notifications for inserted stacks and experience (client side) |
| Optional | [Lightman's Currency](https://www.curseforge.com/minecraft/mc-mods/lightmans-currency) | Coins route into the equipped wallet, with a pickup toast for every wallet insert |
| Optional | [Better Party](https://www.curseforge.com/minecraft/mc-mods/better-party), [Open Parties and Claims](https://modrinth.com/mod/open-parties-and-claims), [FTB Teams](https://www.curseforge.com/minecraft/mc-mods/ftb-teams) | Party membership for shared kill loot (tried in that order under `AUTO`) |

Every optional dependency is gated behind `ModList.isLoaded` in exactly one adapter
class, so the mod works — and never class-loads their code — without them.

## Configuration

SERVER values live in `quick_loot-server.toml`; all of them can be edited in-game (see
below). Highlights:

| Key | Default | What it does |
|---|---|---|
| `drops.enabled` | `true` | Master switch for all Quick Loot behaviour |
| `drops.mobDrops` / `drops.blockDrops` | `true` | Insert mob / block drops (experience follows the same gates) |
| `drops.useBackpacks` | `true` | Spill overflow into carried Sophisticated Backpacks |
| `drops.coinsToWallet` | `true` | Route Lightman's Currency coins into the equipped wallet on every insert path |
| `drops.lootJournalNotify` | `true` | Send inserted stacks and awarded experience to Loot Journal |
| `quickDeposit.enabled` / `quickDeposit.scope` | `true` / `LOOT_ONLY` | The quick-insert button and how much of it sees |
| `party.enabled` / `party.lootRule` / `party.lootAmount` | `true` / `KILLING_BLOW` / `OWNER_ONLY` | Party sharing: who receives, and how much |

## In-game config editing

Mods built with this template can be edited in-game with
[Configured](https://www.curseforge.com/minecraft/mc-mods/configured) - install
it like any other mod; it adds no code to this one. Open the mod list, select a
mod, press Config, and change values directly: Configured writes through
NeoForge's config system, so a reload applies the new values without restarting
the game.

This mod pre-wires the config-screen background in
`src/main/templates/META-INF/neoforge.mods.toml`
(`[modproperties.${mod_id}] configuredBackground`) - point it at any texture you
like. Note Configured only shows a menu for mods that actually register a
config: a mod with no registered settings has nothing to show.

## Credits

- Built on [NeoForge](https://neoforged.net/).
- Optional integrations: [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks),
  [Loot Journal](https://www.curseforge.com/minecraft/mc-mods/loot-journal),
  [Lightman's Currency](https://www.curseforge.com/minecraft/mc-mods/lightmans-currency),
  [Better Party](https://www.curseforge.com/minecraft/mc-mods/better-party),
  [Open Parties and Claims](https://modrinth.com/mod/open-parties-and-claims) and
  [FTB Teams](https://www.curseforge.com/minecraft/mc-mods/ftb-teams) — all through
  their public APIs only.

## License

GNU Lesser General Public License v2.1 — see [`LICENSE`](LICENSE).
Copyright (c) 2026 Bitwise Madness.

## AI disclosure

Parts of this project were written with the assistance of AI coding tools.
AI-assisted code, data and documentation ship only after human review by the
maintainers.
