# Repo rules — quick-loot

Repo-specific rules and notes. The org-wide `AGENTS.md` beside this file is
maintained in [`neoforge-mod-template`](https://github.com/HexaHexes/neoforge-mod-template)
and pulled in verbatim — **never hand-edit it.** Everything in this file is this
repo's own and is edited freely; keep it aligned with the org rules rather than
restating them.

**Repo:** `HexaHexes/quick-loot` · **Mod ID:** `quick_loot` · **Branch:** `main`
**Platform:** Minecraft 1.21.1 / NeoForge 21.1.259 / JDK 21 · **Jar:** `build/libs/quick_loot-<version>.jar`

Inserts mob and block drops straight into the player's inventory (with
Sophisticated Backpacks overflow), awards the accompanying experience directly
instead of spawning orbs, adds a server-validated quick-insert button on loot
containers, shares kill loot among party members, and routes Lightman's Currency
coins into the player's equipped wallet — with Loot Journal pickup notifications
for everything that lands, including every wallet insert.

**Dependencies**
- Required: Minecraft, NeoForge.
- Optional: Sophisticated Backpacks (+ Sophisticated Core) for backpack overflow,
  Loot Journal (client) for pickup toasts, Lightman's Currency for wallet routing,
  and Better Party / Open Parties and Claims / FTB Teams for party membership.
  Each mod's types live in exactly one adapter class behind `ModList.isLoaded`
  (`compat/BackpackIntegration`, `compat/LightmansCurrencyIntegration`,
  `client/LootJournalClientIntegration`, `party/*Adapter`); nothing else may
  import their packages.

**Test:** `./gradlew runGameTestServer` — `QuickLootGameTests` +
`LightmansCurrencyGameTests` + `SophisticatedBackpackGameTests` (28 tests).
`expectedTests = 28` in `build.gradle`
must match the `@GameTest` count. JUnit (`src/test`) covers the pure party
matrix and pure config types; GameTests cover everything that needs a live
server.

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
  quick-deposit, wallet routing) goes through `DropInserter.insert` in wallet →
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
  `ResourceLocation.fromNamespaceAndPath(MOD_ID, ...)` — `createType(String)` is
  minecraft-namespaced and crashes the server at registration (first boot caught
  this). Channel ids: `quick_loot:inserted`, `quick_loot:container_flag`,
  `quick_loot:quick_deposit`.
- **Visibility discipline**: helpers stay package-private unless a test seam
  forces otherwise (`XpAwards` is package-private; `LootContainerLatches.load` /
  `forLevel` are deliberately *not* widened — NBT round-trip is covered by the
  `InsertedPayload` STREAM_CODEC test instead). The GameTest seams are the
  `handleLivingDrops` / `handleLivingXpDrop` / `handleBlockDrops` bridge overloads
  and `WalletCoinObserver.capture`.
- **Menu whitelist lockstep** (issue #13): the client button gate and the
  server's `QuickDeposit.run` validation must both go through
  `MenuWhitelist.containerOf` (Chest / Shulker / Hopper / Dispenser) - never
  a direct `instanceof` - because the `quickDeposit.menus` config check folds
  into that one method. NeoForge syncs SERVER configs to the client during
  login (`net.neoforged.neoforge.network.ConfigSync` reads
  `ModConfigs.getConfigSet(Type.SERVER)`), so both sides read the same value
  with no whitelist payload of our own; a modded client on an unmodded server
  falls back to local defaults, which the server still revalidates. Evidence
  is a separate latch (`LootContainerLatches`) on top: `getLootTable()` nulls
  on first open; `ALL_CONTAINERS` is the escape hatch.
- **One behaviour, one server key** (issue #13): every gameplay axis has its
  own SERVER-spec key - items (`mobDrops`/`blockDrops`) and XP
  (`mobExperience`/`blockExperience`) are independent pairs, the LC death-coin
  scoop is `lightmansCoins` (AND-gated under `mobDrops`, never alone), and
  deposit has `requireCarried` + `menus`. New behaviour gets a new key with a
  behaviour-neutral default; never fold one axis into another's toggle. Pure
  config types (e.g. `DepositMenuKind`) stay free of Minecraft imports so the
  JUnit suite can pin them.
- **Party source AUTO order**: Better Party → OPaC → FTB Teams. OPaC's adapter
  prefers owner-then-uuid order; FTB filters to party-type teams only.
- **GameTest harness** (`src/gametest`): mock players join through the real
  `placeNewPlayer`, so every payload a staged mod may send at login must be in
  `TestSupport.AD_HOC_CHANNELS` — new misses surface as
  `GameTest join aborted (...)` warnings naming the channel; extend the list from
  the run log. Generated mock names stay ≤ 16 chars (FTB team syncs use
  username-limited codecs), `run/world` is wiped before every run (persisted
  parties/teams from a prior run break the next run's joins), and
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
