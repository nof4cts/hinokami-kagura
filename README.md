# Shrine: Cursed Technique (Forge 1.20.1)

A cursed technique of cutting and fire, inspired by the King of Curses' moves. Every effect is drawn by the
mod's own shaders over the real frame (no Minecraft particles, no block models): cuts, flames, the domain
and the cut through the world, with anime impact frames, title cards and cutscenes.

Requires **Player Animator** (`playeranimator`, Forge 1.20.1) for the body animations.

## Moves (keys rebindable under Controls > Shrine)
| Key | Move | |
|---|---|---|
| G (tap) | **Dismantle** | a crescent of cursed energy sweeps out from the hand: everything along it is cut, the world included |
| G (hold) | **Barrage** | sixteen Dismantles in a second and a half, following your aim |
| H | **Cleave** | touch range: a net of cuts across the target, adapted to it: it comes apart |
| J | **Spiderweb** | a palm to the ground: radial cuts and rings race out 24 blocks and the ground collapses |
| N | **Black Flash** | a blow with black lightning: one-shot, hurled away, the ground dented |
| Y | **Open** | cutscene: fire pours from the open hand, becomes a bow and an arrow, is loosed: a pillar of fire, crater, molten floor |
| U | **Domain Expansion** | cutscene: the hand sign, the cards, the world turns; then for 14 s everything alive within 110 blocks is cut over and over and everything else is cut down to your footing |
| I | **World-Cutting Slash** | cutscene: three hand signs, then one cut through space: 1000 blocks long, sky to void; the picture of the world splits along it |

Damage defaults to 100000 (one-shots anything vanilla). The caster can't be hurt during a cutscene move.
Hold **Shift** to skip a cutscene.

## Config
**Client** (`config/shrine-client.toml`): `bloom`, `aberration`, `filmGrain`, `volumeSteps` (fire quality),
`cameraShake`, `flash`, `impactFrames` (turn off if rapid flashing bothers you).

**Server** (`serverconfig/shrine-server.toml`): `damage`, `cutBlocks`, `cooldownScale`, `dismantleLength`,
`fugaRadius`, `fugaRange`, `domainRadius`, `domainSeconds`, `worldSlashLength`, `blocksPerTick`,
`cutsceneRadius`, `opOnly`.

## Notes
- All sounds are synthesised from scratch (`tools/gen_sounds.py`); body animations are generated keyframes
  (`tools/gen_anims.py`); shaders can be previewed offline (`tools/render_technique.py`).
- Character designs (the King of Curses himself, his shikigami, his shrine) are not included.
