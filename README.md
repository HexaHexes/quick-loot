# Example Mod

A short, player-facing pitch goes here: one or two sentences on what the mod adds
or changes, written for someone deciding whether to install it.

## What it does

- Describe the first mechanic in plain terms.
- Describe the second mechanic in plain terms.

## Dependencies

| | Mod | Why |
|---|---|---|
| Required | Minecraft | — |
| Required | NeoForge | — |
| Optional | Example Library | What breaks or is missing without it |

## In-game config editing

Mods built with this template can be edited in-game with
[Configured](https://www.curseforge.com/minecraft/mc-mods/configured) - install
it like any other mod; it adds no code to this one. Open the mod list, select a
mod, press Config, and change values directly: Configured writes through
NeoForge's config system, so a reload applies the new values without restarting
the game.

This template pre-wires the config-screen background in
`src/main/templates/META-INF/neoforge.mods.toml`
(`[modproperties.${mod_id}] configuredBackground`) - point it at any texture you
like. Note Configured only shows a menu for mods that actually register a
config: a template-derived mod with no registered settings has nothing to show.

## Using this template

This repository is a **template** for new NeoForge mods in this org. To start a
mod from it: create a new repository from this template (or copy the folder),
then work through the **rename checklist** below. Every step is mechanical.

### Rename checklist

The mod's identity is deliberately concentrated so a rename touches a handful of
known places. Search the repo for each of these before you start.

1. **`gradle.properties`**: set `mod_id`, `mod_name`, `mod_version`,
   `mod_group_id`, `mod_authors`, `mod_description`. This is the single source
   of truth: `build.gradle` reads these, and the `neoforge.mods.toml`
   placeholders are filled from them at build time.
2. **`settings.gradle`**: set `rootProject.name` to the same value as `mod_id`.
3. **`build.gradle`**: `archivesName` already reads `mod_id`; leave it unless
   you want a jar name that differs from the mod id.
4. **Java package**: move `src/main/java/org/bitwisemadness/examplemod/` and
   `src/gametest/java/org/bitwisemadness/examplemod/` to your new
   `mod_group_id` path, and update the `package` declarations.
5. **`ExampleMod.MOD_ID`**: set the constant to the new `mod_id`. It is the
   value `@Mod` is annotated with, and it must match `mod_id` exactly.
6. **Mixins config**: rename `src/main/resources/examplemod.mixins.json` to
   `<mod_id>.mixins.json`, and set its `package` to your mixin package. The
   `[[mixins]]` entry in `src/main/templates/META-INF/neoforge.mods.toml`
   already references it by `${mod_id}.mixins.json`.
   See [`docs/mixin-rules.md`](docs/mixin-rules.md) for the mixin rules:
   no inherited-member `@Shadow`s, no refmap, and how to verify client mixins
   (CI never loads the `client` array).
7. **GameTest template structure**: if you keep the smoke test, the structure
   lives at `src/gametest/resources/data/<mod_id>/structure/empty.nbt`; move it
   under your new mod id. The smoke test's `@GameTest(template = "empty")`
   resolves it.
8. **`agents/REPO.md`**: fill in the repo header (repo, mod id, jar name), the
   description, the dependencies, the test line and the gotchas. Do not edit
   `agents/AGENTS.md`: it is pulled verbatim from the template repo and only
   changes there.
9. **Publishing block in `build.gradle`**: set the GitHub Packages URL in the
   `publishing` block to this repository
   (`https://maven.pkg.github.com/HexaHexes/<repo-name>`); the artifactId and the
   pom metadata already read from `gradle.properties`.
10. **`NOTICE`**: replace `<project name>` with the mod name and add attribution
    for anything the mod reads through public APIs or includes. The file is
    mandatory and must never be deleted.

Search for leftovers before your first build:

```
examplemod
Example Mod
org.bitwisemadness.examplemod
```

All three should return nothing once the rename is complete.

## Credits

- Built on [NeoForge](https://neoforged.net/).

## License

GNU Lesser General Public License v2.1 — see [`LICENSE`](LICENSE).
Copyright (c) 2026 Bitwise Madness.

## AI disclosure

Parts of this project were written with the assistance of AI coding tools.
AI-assisted code, data and documentation ship only after human review by the
maintainers.
