# Mixin rules

Every rule here was paid for by a real failure: `tensura-greatsage-growth` alpha.7
shipped a client mixin that **failed to apply in the production pack** while every
CI check stayed green:

```
[FATAL] [mixin/]: Mixin apply for mod tensura_greatsage_growth failed
  ...SageMenuScreenMixin -> dev.dedek.greatsage.client.SageMenuScreen:
  InvalidMixinException: @Shadow field width was not located in the target class
  dev.dedek.greatsage.client.SageMenuScreen. No refMap loaded.
```

The game logged the FATAL and carried on, so the feature the mixin existed for
simply never happened - a silently broken mod (HexaHexes/tensura-greatsage-growth#15,
found while triaging HexaHexes/tensura-xaero-integration#50; systemic side tracked
in neoforge-mod-template#92). The rules below are how that cannot recur.

## The rules

### 1. Never `@Shadow` a member inherited from a Minecraft class

Mixin resolves a `@Shadow` **field** against the *target class*. Our broken mixin
shadowed `width`/`height`/`minecraft`/`font` - all declared on `Screen`, not on the
target - and the lookup failed (`was not located in the target class ... No refMap
loaded`). `@Shadow` is safe only for members the target class itself declares.

### 2. Reach inherited client state with plain bytecode instead

Ordinary field/method access in a mixin handler needs no mixin machinery at all:

```java
// Screen dimensions: 1.21.1's Screen has no getWidth()/getHeight() - the fields
// are public, so a direct access on the declaring class compiles and just works.
Screen screen = (Screen) (Object) this;
int w = screen.width;
int h = screen.height;

// The client, its public font, and the player: never shadow these.
Minecraft minecraft = Minecraft.getInstance();
```

`javap -p -c build/classes/java/main/<your mixin>` should show plain
`getfield net/minecraft/client/gui/screens/Screen.width:I` and no shadowed fields.

### 3. If you do `@Shadow`, match the runtime declaration exactly

Only shadow what the target declares, with the modifiers it actually has. Check
the runtime artifact, not your IDE:

```
javap -p -classpath <minecraft jar from libraries/> net.minecraft.client.gui.screens.Screen
```

(The broken shadow said `protected int width`; the runtime declares `public int
width` - modifier mismatch stacked on the inherited-lookup failure.)

### 4. No refmaps - keep `refmap` out of `mixins.json`

NeoForge 1.21.1 runs Mojmap at runtime: the shipped client jar carries literal
mojmap member names, and injection descriptors resolve without a refmap (in the
same pack session, this template's descriptor-based mixins applied while dozens of
legacy mods logged `Reference map '...refmap.json' could not be read` warnings).
Do not add a `refmap` key or refmap generation; treat those warnings from other
mods as legacy noise.

### 5. Inject only into methods the target class declares

`@Inject(method = ...)` must name a method on the target class itself - inherited
methods are not a reliable injection point. Verify with:

```
javap -p -classpath <dependency jar> <target class>
```

## Verifying before release

CI proves less than it looks like it does:

- `runGameTestServer` runs a **dedicated server** - the `client` array of
  `mixins.json` is never loaded there.
- The unit tests are **pure JVM** - no mixin, no Minecraft classes.

So for any change touching the `client` array:

1. `./gradlew build` (checkstyle, SpotBugs, unit) and `./gradlew runGameTestServer`.
2. `javap -p -c` on the changed mixin class - confirm rule 2's shape (plain
   getfield/invokestatic, no shadow fields you did not declare on the target).
3. **Launch `./gradlew runClient` once.** This is the only thing that actually
   applies client mixins. For user-visible screen/overlay changes, confirm once in
   a production-mapping environment (the real pack) before cutting a release.
4. Grep `logs/latest.log` for `Mixin apply for mod`, `Failed to apply mixin`,
   `No refMap loaded` - any hit is a release blocker, not a warning.
