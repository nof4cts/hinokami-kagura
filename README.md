# Sun Breathing (Forge 1.20.1)

Hinokami Kagura–inspired combat: a breath resource, Total Concentration Breathing, a Nichirin Blade
that ignites from black to crimson, and all **twelve cinematic forms**:

| # | Form | What it does |
|---|---|---|
| I | Dance | Fast diagonal slash with a forward step |
| II | Clear Blue Sky | Hop + two rising full-circle slashes |
| III | Raging Sun | Huge 250° sweep, fire races across the ground |
| IV | Burning Bones, Summer Sun | Five spinning cuts, the last one heavy |
| V | Setting Sun Transformation | Dash that cuts everything on the path, afterimages behind you |
| VI | Solar Heat Haze | Backward dodge, brief immunity, heat-shimmer decoy; mobs lose track of you |
| VII | Beneficent Radiance | Overhead lunge ending in a sunburst that blasts enemies back |
| VIII | Sunflower Thrust | Long piercing thrust that hits everything in a line, flower of flame at the tip |
| IX | Dragon Sun Halo Head Dance | Leap, four spiralling halo cuts in the air, slow fall |
| X | Fire Wheel | Forward somersault, a wheel of fire slams down in front |
| XI | Fake Rainbow | Six weaving cuts with sidesteps and ghosts |
| XII | Flame Dance | Chains I → III → X → VII, then a full-circle eruption |

| | |
|---|---|
| Minecraft / Forge | 1.20.1 / 47.4.0 (any 47.1+ works) |
| Java | 17 (Gradle will auto-download it via the toolchain) |
| Dependencies | GeckoLib 4.4+ (blade model), Player Animator 1.0.2-rc1+1.20 (body animations) |

---

## 1. Build & test guide

### First-time setup
1. Install a JDK 17 (Temurin is fine). Gradle runs on 17 and also auto-provisions it for compilation.
2. Unzip the project and open a terminal in the folder containing `build.gradle`.
3. Run:
   ```
   ./gradlew build          # Windows: gradlew.bat build
   ```
   The first run downloads Minecraft, Forge, GeckoLib and Player Animator and decompiles the game.
   Expect 5–15 minutes. Success = `BUILD SUCCESSFUL` and a jar in `build/libs/sunbreathing-0.1.0.jar`.
4. **If it fails, copy the first `error:` block and send it to me.** I couldn't run Gradle in my
   sandbox, so this is the step that confirms the code compiles on your machine.
5. IDE (optional): open the folder in IntelliJ IDEA as a Gradle project, then run `./gradlew genIntellijRuns`.

### Run the game in dev
```
./gradlew runClient
```
Create a **Creative** world with cheats.

### Test checklist for Stage 1
| Step | What to do | What you should see/hear |
|---|---|---|
| 1 | Open the Combat tab, take the **Nichirin Blade** | 3D black katana in hand; HUD appears bottom-left (sun emblem + bar, "I · Dance") |
| 2 | Hold **Z** | Radial wheel with all twelve forms. Point at one and release to select |
| 3 | Press **R** facing a zombie / armor stand | Body swing (1st and 3rd person, F5), forward step, diagonal crescent of fire with white-hot edge, flame + ember + spark particles, whoosh + ignite |
| 4 | Land the hit | White flash, light rays, 2–4 frame freeze, camera shake, FOV punch on heavy hits, slash + sub-thump + crackle, target ignites |
| 5 | Spam **R** | Cooldown pie sweeps over the emblem; extra presses are ignored |
| 6 | Press **V** | Inhale + ignite sound, blade flares and fades from black to crimson-orange with shimmer, aura puffs, ember trail when running, faster movement, soft breathing loop, emblem spins |
| 7 | Stay in Total Concentration | Bar slowly drains; at 0 you gasp and it ends |
| 8 | Stand still / walk / sprint | Fast / slow / no regeneration |
| 9 | Multiplayer: `runServer` + two clients | Both see each other's swings, arcs, aura and glowing blades; only the server decides hits |
| 10 | `config/sunbreathing-client.toml` → `particleDensity = "LOW"`, `glowShader = false` | Fewer particles; slashes use the vanilla-shader fallback |

Server balance lives in `<world>/serverconfig/sunbreathing-server.toml` (damage, cooldowns, breath costs,
regen, Total Concentration speed/upkeep, fire, PvP).

### Controls (rebindable under Options → Controls → Sun Breathing)
- **R**: use selected form
- **V**: toggle Total Concentration Breathing
- **Z (hold)**: form wheel (release or click to pick)

### Recipe
```
. . I      I = iron ingot
. M .      M = magma cream
B . .      B = blaze rod
```

---

## 2. Architecture

```
com.sunbreathing
├── SunBreathingMod          entry point, registries, configs, network
├── config/SunConfig         SERVER (balance, synced to clients) + CLIENT (visual/comfort) configs
├── registry/                items, sounds, particle types
├── item/                    NichirinBladeItem (GeckoLib GeoItem), NichirinTier
├── breath/                  per-player capability, regen/upkeep tick, Total Concentration logic
├── forms/                   Form enum, FormExecutor (all rules), ServerForms (timelines), Combat (hit shapes)
├── network/                 C2S intents, S2C presentation packets
└── client/                  (never loaded on a dedicated server)
    ├── anim/PlayerAnims     Player Animator layer + hit-stop speed control
    ├── fx/                  SlashArc mesh + renderer, shaders, SunFx particle recipes,
    │                        CameraFx (shake/FOV), VfxClock (hit-stop), ConcentrationFx, FormVfx
    ├── hud/                 procedural breath HUD, radial form wheel
    ├── particle/            additive particles + palette
    ├── render/              blade renderer + ignition (emissive) layer
    └── sound/               breathing loop
```

**Server-authoritative flow for a form:**
`R` → `C2SUseForm` → `FormExecutor` (blade? cooldown? breath?) → `ServerForms.dance` broadcasts
`S2CFormCast` and schedules lunge (tick 1) + hit (tick 3) → `Combat.arc` + `Combat.strike` → each
landed hit broadcasts `S2CImpact` → clients play impact VFX; attacker/victim also get hit-stop, shake and FOV.

Timing constants shared by animation, mesh and hit window live in `forms/FormTiming`.

---

## 3. Docs
- `docs/ASSETS.md`: every texture/model/animation/sound with exact sizes and a Blockbench guide
- `docs/POLISH.md`: suggested polish passes after the first working version
- `tools/gen_textures.py`, `tools/gen_sounds.py`: regenerate the placeholder art/audio
