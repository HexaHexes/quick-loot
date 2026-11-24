# Repo rules — quick-loot

Repo-specific rules and notes. The org-wide `AGENTS.md` beside this file is
maintained in [`neoforge-mod-template`](https://github.com/HexaHexes/neoforge-mod-template)
and pulled in verbatim — **never hand-edit it.** Everything in this file is this
repo's own and is edited freely; keep it aligned with the org rules rather than
restating them.

**Repo:** `HexaHexes/quick-loot` · **Mod ID:** `quick_loot` · **Branch:** `neoforge-1.21.1` (the loader line; this is the default branch - no `main`)
**Platform:** Minecraft 1.21.1 / NeoForge 21.1.259 / JDK 21 · **Jar:** `build/libs/quick_loot-neoforge-1.21.1-<version>.jar`

Inserts mob and block drops straight into the player's inventory (with
Sophisticated Backpacks overflow), awards the accompanying experience directly
instead of spawning orbs, shares kill loot among party members, and routes
Lightman's Currency coins into the player's equipped wallet — with Loot
Journal pickup notifications for everything that lands, including every
wallet insert.

**Dependencies**
- Required: Minecraft, NeoForge.
- Optional: Sophisticated Backpacks (+ Sophisticated Core) for backpack overflow,
  Loot Journal (client) for pickup toasts, Lightman's Currency for wallet routing,
  and Better Party / Open Parties and Claims / FTB Teams for party membership.
  Each mod's types live in exactly one adapter class behind `ModList.isLoaded`
  (`compat/BackpackIntegration`, `compat/LightmansCurrencyIntegration`,
  `client/LootJournalClientIntegration`, `party/*Adapter`); nothing else may
  import their packages. Better Party's *client* API is the one exception:
  `client/BetterPartySettingsPage` and `client/PartyLootSettingsScreen` (its
  party-leader settings page, issue #21) may import `dev.betterparty` client
  types - and nothing else may.

**Test:** `./gradlew runGameTestServer` — `QuickLootGameTests` +
`LightmansCurrencyGameTests` + `SophisticatedBackpackGameTests` (28 tests).
`expectedTests = 28` in `build.gradle`
must match the `@GameTest` count. JUnit (`src/test`) covers the pure party
matrix and the override parser; GameTests cover everything that needs a
live server.

**Gotchas**
- **Never cancel a drop event** — mutate instead: remove absorbed `ItemEntity`s
  from `getDrops()`, `setItem(leftover)` on partial ones, `setDroppedExperience(0)`
  on XP. Cancelling `BlockDropsEvent` suppresses `spawnAfterBreak`; zeroing is
  what makes vanilla's `popExperience` a no-op. Block XP mirrors popExperience's
  own downstream gates (`RULE_DOBLOCKDROPS`, `!restoringBlockSnapshots`) before
  awarding.
- **XP ordering memo**: `LivingExperienceDropEvent` fires before `LivingDropsEvent`,
  and the ender dragon re-fires XP across its death animation — so distribution is
  resolved at most once per kill via the `WeakHashMap` `RESOLVED_KILLS` keyed by
  (killed entity, killer UUID). Do not clear it on read; a killer mismatch must
  resolve fresh.
- **Single insert funnel**: every path (mob drops, block drops, party shares,
  wallet routing) goes through `DropInserter.insert` in wallet →
  backpack → inventory order. The backpack stage is Sophisticated Backpacks'
  own `InventoryHelper.runPickupOnPickupResponseUpgrades` chain (issue #7):
  only a Pickup / Advanced Pickup / Magnet / Advanced Magnet upgrade claims
  anything, its filter and settings decide, and the old
  `getInventoryForInputOutput()` overflow path is gone (it superseded #1's
  overflow clause). The `insertedOut` baseline is taken *after* the wallet
  stage: the routed portion is never self-notified - `WalletCoinObserver`
  owns wallet toasts, which is what makes double-toasts impossible.
- **LC death coins are world entities** (issue #11): Lightman's Currency
  spawns mob coins on `LivingDeathEvent`, which fires *before* the drops
  event, so they are never in `event.getDrops()`. `DropEvents.collectFreshCoins`
  scoops the age-0 coins around the victim into the shared distribution -
  never widen that boundary past same-tick coins at the death site, or it
  starts stealing ground coins that belong to vanilla pickup (and LC's own
  wallet divert).
- **Observer invariants** (issue #4): value-based (Σ `MoneyValue.getCoreValue`),
  never slot-based — `WalletItem.PickupCoin` auto-exchanges and re-sorts slots.
  Baseline fingerprint is the equipped wallet's *identity* (`==`, never `equals` —
  deposits mutate in place). Baselines clear while toasts are gated, so re-enabling
  never toasts a backlog. `coinStacksFor` reads the wallet's *data contents*
  (`WalletItem.getDataWrapper(...).getContents()`) — `WalletHelpers.getWalletContainer`
  is a one-slot container holding the wallet item itself, not its coins.
- **Payload types**: build `CustomPacketPayload.Type` with
  `ResourceLocation.fromNamespaceAndPath(MOD_ID, ...)` - `createType(String)` is
  minecraft-namespaced and crashes the server at registration (first boot caught
  this). Channel id: `quick_loot:inserted`.
- **Channels register optional** (`event.registrar("1").optional()`): a
  *required* channel makes NeoForge's negotiation disconnect any client that
  lacks it - vanilla or a modded client without this mod - so required
  channels would force every player to install Quick: Loot!. Optional
  channels are disabled for those clients instead (NeoForge's own built-ins
  register the same way), and clientbound payloads are only ever delivered to
  clients that negotiated the channel. Clients without the mod get the full
  gameplay but no Loot Journal toast; never flip this back to
  required.
- **Visibility discipline**: helpers stay package-private unless a test seam
  forces otherwise (`XpAwards` is package-private). The GameTest seams are the
  `handleLivingDrops` / `handleLivingXpDrop` / `handleBlockDrops` bridge overloads
  and `WalletCoinObserver.capture`.
- **One behaviour, one server key** (issue #13): every gameplay axis has its
  own SERVER-spec key - items (`mobDrops`/`blockDrops`) and XP
  (`mobExperience`/`blockExperience`) are independent pairs, and the LC
  death-coin scoop is `lightmansCoins` (AND-gated under `mobDrops`, never
  alone). New behaviour gets a new key with a behaviour-neutral default;
  never fold one axis into another's toggle.
- **Config reads need a loaded spec** (issue #17): `ScreenEvent.Init.Post`
  fires from `Minecraft.<init>` *before* `ConfigTracker.loadConfigs`, and an
  unloaded `ConfigValue.get()` throws `IllegalStateException` straight
  through the event bus. Any handler that can run pre-load must do its
  structural checks first and gate config reads on `SPEC.isLoaded()`;
  in-world handlers (drops, ticks, interactions) run long after config load
  and need no guard. No pre-load surface remains (the quick-insert button hook
  went with the feature, issue #19) - audit any new client handler against this rule.
- **Party source AUTO order**: Better Party → OPaC → FTB Teams. OPaC's adapter
  prefers owner-then-uuid order; FTB filters to party-type teams only.
- **Per-party settings overrides** (issue #21): effective value = party
  override, else server config; `enabled` composes as
  `config AND override` (default `true`), so the config stays the admin
  master switch and a party can only opt out. Read the RAW overrides map
  (`PartyBridge.settingOverrides` → Better Party's
  `partySettingOverrides`, or FTB's team properties), never a "get the
  setting" convenience API: those return the definition default and cannot
  distinguish "the party never chose" from "chose the default", which would
  let property defaults override a reconfigured server. FTB cannot express
  unset at all, so its property default is the `SERVER_DEFAULT` inherit
  sentinel that `FtbTeamsAdapter` drops from the map. All three definitions
  (`quick_loot:enabled`, `quick_loot:loot_rule`, `quick_loot:loot_amount`)
  register at mod construction behind `isLoaded` on both physical sides -
  Better Party validates writes against its registry and the client encodes
  requests with the same definitions - while FTB's properties register on
  `TeamEvent.COLLECT_PROPERTIES` (fired per team collection, after mod
  construction). Unparseable stored values fall back to the config, never
  throw; the resolved `PartyAmount` rides the `Distribution` record so the
  item and XP paths cannot disagree.
- **GameTest harness** (`src/gametest`): mock players join through the real
  `placeNewPlayer`, so every payload a staged mod may send at login must be in
  `TestSupport.AD_HOC_CHANNELS` — new misses surface as
  `GameTest join aborted (...)` warnings naming the channel; extend the list from
  the run log. Generated mock names stay ≤ 16 chars (FTB team syncs use
  username-limited codecs), party/team state in `run/world` is wiped before
  every run (persisted parties/teams from a prior run break the next run's
  joins), and
  `GameTestServerFixes` (gametest source only, never jarred) installs the profile
  cache vanilla's `GameTestServer` omits - OPaC dereferences it at server-about-to
  -start.
- **GameTest runs are capped at 10 minutes.** Vanilla places the structure grid
  at a random far position and a test only starts once every structure chunk is
  entity-ticking; a first run at an unlucky position can stall forever (those
  tests never reach their timeout), so the task fails fast instead of hanging.
  Rerun, or delete `run/world` to roll a new position. A kept world keeps its
  seed, grid position and force-loaded chunks, so local reruns are stable;
  only party/team state is wiped between runs (stale FTB Teams data breaks the
  next run's mock joins).
- **LC pin**: CurseMaven `lightmans-currency-472521:9048585` (= 1.21-2.3.0.6) is
  both the `compileOnly` and the `gametestMods` pin; mods.toml's optional range
  floor must move with it. FTB Library uses project `ftb-library-forge-404465`
  (project `ftb-library-846592` has no jar on CurseMaven anymore).
