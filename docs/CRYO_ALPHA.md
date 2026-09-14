# Cryo Lance animation alpha — 0.5.0-alpha.1

This branch adds an animated Cryo Lance to the existing eight-weapon mod. The other seven weapon models are unchanged. Minecraft 1.21.8 / Fabric only.

## What is implemented

- A 3D lance with four articulated prongs, an independently rotating core and collar.
- Idle, frost stream, glacial nova, Absolute Zero raise/slam, and shatter clips.
- Two counter-rotating textured rune discs per cast, driven by one server event. No per-frame server packets for the discs. Up to 24 active cast visuals, visible within 64 blocks; expire on time and clear on disconnect/dimension change.
- The existing ultimate ice spikes, terrain effects, damage and layered vanilla sounds remain. Its slam animation keyframe is at 0.65 seconds, matching the scheduled tick-13 impact, subject to network/render timing.
- Frost stream cooldown now matches its 40-tick channel, preventing stacked channels. Damage callbacks skip when the caster dies, changes dimensions or puts the weapon away.
- Early shatter cancels pending charge, freeze-wave and spike callbacks. Shatter uses the original cast world and center even if the caster moves.

The disc is a horizontal plane at the cast position. It is depth-tested and can be hidden by uneven terrain; it does not conform to hills. This pass does not add full-body player poses, camera shake, new recorded sounds, or smooth growing mesh replacements for the existing block spikes. Weapon root motion is not player skeleton animation.

## Validation

GitHub Actions run 34895642536 successfully compiled and remapped commit d443d893a8dc58192ab4ddd60bb9068b71de3582 with Java 25, Gradle 9.7.1, Fabric Loom 1.17.20, Fabric API 0.136.1+1.21.8 and GeckoLib 5.2.2. There were no Java tests in the original project; `build` is a compile/package check, not a gameplay test.

`cryo-model-preview.png` and `cryo-model-preview.gif` are software previews generated from the actual geometry/keyframes. They are not Minecraft screenshots and do not verify Minecraft's camera/held-item transforms, lighting, animation transitions, resource loading, dedicated-server startup or multiplayer behavior.

## Install for testing

Use a separate Fabric 1.21.8 instance and a disposable world first. Install all three dependencies on both that client and any test server:

1. `spacemod-0.5.0-alpha.1.jar` from the successful Actions artifact (extract the ZIP).
2. Fabric API `0.136.1+1.21.8`.
3. GeckoLib **5.2.2 for Fabric 1.21.8**.

Remove the older spacemod JAR from that test instance's mods folder; never keep two versions together. Minecraft needs Java 21 or newer. Building this repository uses Java 25 for its existing Gradle/Loom versions.

Give yourself `/give @s spacemod:cryo_lance`. Right-click streams frost, sneak + right-click casts nova, R activates Absolute Zero, and R again shatters it. Check first person and F5, then have a second player watch. Test early shatter during the charge and moving away before shatter. Verify terrain cleanup after about 15 seconds. Try switching held items during the stream. Check logs for resource and renderer errors.

Do not replace the live TickHosting installation until those checks pass. For a later deployment, back up/download the world, stop the server, replace the old spacemod JAR, add the matching GeckoLib dependency, and give all players the same files. Do not reinstall the hosting server or change Minecraft versions.

## Reproduce assets

Install Python packages `Pillow` and `numpy`, then run:

```
python scripts/generate_cryo_assets.py
python scripts/preview_cryo.py
```

The editable model is `src/main/resources/assets/spacemod/geckolib/models/cryo_lance.geo.json`; the matching clips are under `geckolib/animations`. The generator is authoritative: editing its source preserves changes across regeneration.
