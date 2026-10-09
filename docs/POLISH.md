# Polish passes (after the first working version)

Ordered by impact on game feel per hour of work.

## Pass 1: Tune what's already there (in-game, no new systems)
- **Blade display transforms** in Blockbench (hand, first person, GUI). Biggest visual win for least effort.
- **Dance timing**: watch at 0.25× (`/tick rate` mod or screen recording) and line up the arc, the
  animation's blade crossing, and the 3-tick hit. Adjust `FormTiming` and the keyframes together.
- **Arc shape**: radius/thickness/roll in `FormVfx.dance`; shader constants in `slash_glow.fsh`
  (edge sharpness `smoothstep(0.55, 0.97, v)`, flame scroll speed `t * 7.0`).
- **Hit-stop and shake curves** (`FormVfx.onImpact`, `CameraFx`): heavier forms should get longer stops.
- **Balance**: TTK on a zombie, iron golem and a player in diamond armour; cooldown vs breath economy.

## Pass 2: Missing feel layers
- **Victim reaction**: brief red-orange hurt flash/scorch overlay on the target model and a stagger nudge.
- **Weapon trail on normal swings** (a thin, faint arc) so regular attacks feel related to the forms.
- **Screen-edge heat distortion** while concentrating: a post-process pass (Forge `PostChain`) with a
  noise-offset UV warp masked to the screen edges, intensity = concentration fade. Gated by config and shaderpack detection.
- **Real bloom**: render arcs/particles into an offscreen target, blur, composite additively. Can replace the halo pass.
- **Afterimages** (needed by forms 5 and 6 anyway): snapshot the player model pose each tick into a ring
  buffer and re-render ghosts with a translucent emissive tint.
- **Camera**: brief forward lean during lunges; slight dutch angle on finishers.

## Pass 3: Audio mix
- Replace placeholders with designed sounds (see ASSETS.md).
- Distance-based layer culling: drop the sub layer beyond ~16 blocks.
- Duck the breathing loop briefly on impacts; pitch-randomise every layer by ±5%.
- A short reverb tail variant for caves (check sky light at the impact position).

## Pass 4: Robustness and performance
- Profile with Spark: aim for under 0.5 ms/frame for arcs + particles with 4 players spamming forms.
- Hard cap on live Sun particles (ring buffer) separate from the vanilla limit.
- Iris/Oculus: detect an active shaderpack and switch to the vanilla-safe arc path automatically.
- Server: rate-limit C2S packets per player; reject casts while sleeping/riding/frozen.
- Data-driven forms: move per-form numbers (arc shape, timings) into JSON so designers can iterate without recompiling.

## Pass 5: Presentation
- Form name callout: brief stylised title text on cast (configurable).
- Wheel: show cooldown pies and breath cost per sector; tooltips with damage.
- Advancements for learning/performing each form; optional progression (forms unlocked by use).
- Mob compatibility: extra damage/burn vs undead or a "demon" entity tag.
