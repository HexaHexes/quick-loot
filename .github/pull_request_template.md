## Changeset

<!-- One logical change per PR. Split unrelated work into separate PRs. -->

## Checklist

- [ ] This PR contains **one** logical changeset
- [ ] `./gradlew build` passes locally (compiles)
- [ ] `./gradlew runGameTestServer` passes locally (assert on the
      `required tests passed` line - the exit code is not reliable)
- [ ] If this PR touches a mixin in the `client` array: `./gradlew runClient`
        launched once (the gametest server never loads it - see `docs/mixin-rules.md`)
- [ ] Change is documented; README updated if user-facing
- [ ] This PR adds a feature, or changes one `README.md` already describes,
      and updates `README.md` in this PR
- [ ] No logs, `run/`, or `crash-reports/` committed

## Breaking change

<!-- Tick only if this PR breaks backwards compatibility: datapack schema,
     item/NBT format, or public API. CI uses this label to require that the
     next beta or stable version bump raises major or minor, never patch. -->

- [ ] This change is **breaking** - apply the `breaking` label before merge

## Version bump

<!-- Ruling #81: version bumps ride CI/CD - the post-merge workflow raises
     the -alpha.N slot automatically when work lands, and stable/beta cuts
     go through an isolated gradle.properties PR (#89 scoped the check to
     the mod_version line). A work PR never touches mod_version. -->

- [ ] This PR does **not** change `mod_version` (bumps ride CI/CD post-merge)
- [ ] If this PR changes `gradle.properties` at all, it is dependency pins
      or comments only - those ride with the work
- [ ] If this is a stable/beta release cut: `mod_version` is the **only**
      change in this PR

<!-- A mod_version change combined with any other change is rejected by the
     version-doctrine check; pin/comment-only changes are exempt (#89). -->
