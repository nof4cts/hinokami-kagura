"""
Writes the Player Animator animations for forms II-XII (GeckoLib/Bedrock format) into
assets/sunbreathing/player_animation/. Run from the project root:

    python3 tools/gen_player_anims.py

These are hand-keyed starting points. For polish, import them into Blockbench (Player Animator
plugin), refine, and re-export over the same files. The animation NAME is the lookup key.
Keyframe times are seconds; key the strikes on the ticks in forms/FormTiming.java.
Rotations are degrees [x, y, z]; x negative = arm raised forward/up.
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "sunbreathing", "player_animation")
os.makedirs(OUT, exist_ok=True)
ZERO = [0, 0, 0]


def track(*keys):
    """keys: (time, [x,y,z], easing?)"""
    out = {}
    for k in keys:
        t, v = k[0], k[1]
        entry = {"vector": v}
        if len(k) > 2:
            entry["easing"] = k[2]
        out[f"{t:g}" if t != int(t) else f"{t:.1f}"] = entry
    return out


def write(name, length, bones):
    anim = {"format_version": "1.8.0", "animations": {name: {"animation_length": length, "bones": {
        bone: {"rotation": track(*keys)} for bone, keys in bones.items()}}}}
    with open(os.path.join(OUT, name + ".json"), "w") as f:
        json.dump(anim, f, indent=1)
    print("wrote", name)


Q, C, S, L = "easeOutQuad", "easeOutCubic", "easeInOutSine", "linear"

# II. Clear Blue Sky: hop with a full body spin and two wide circular cuts
write("form_clear_blue_sky", 0.7, {
    "body": [(0, ZERO), (0.15, [0, -180, 0], L), (0.35, [0, -360, 0], Q), (0.7, [0, -360, 0])],
    "right_arm": [(0, ZERO), (0.1, [-110, 0, 70], Q), (0.2, [-100, 0, -50], C), (0.3, [-115, 0, 75], C),
                  (0.4, [-95, 0, -55], C), (0.7, ZERO, S)],
    "left_arm": [(0, ZERO), (0.1, [-60, 0, -40], Q), (0.4, [-60, 0, -40]), (0.7, ZERO, S)],
    "right_leg": [(0, ZERO), (0.15, [-45, 0, 0], Q), (0.45, [-10, 0, 0], S), (0.7, ZERO, S)],
    "left_leg": [(0, ZERO), (0.15, [-25, 0, 0], Q), (0.45, [-5, 0, 0], S), (0.7, ZERO, S)],
})

# III. Raging Sun: deep wind-up to the right, one massive horizontal sweep to the left
write("form_raging_sun", 0.8, {
    "torso": [(0, ZERO), (0.15, [0, 45, 0], Q), (0.25, [0, 45, 0]), (0.35, [0, -65, 0], C), (0.8, ZERO, S)],
    "right_arm": [(0, ZERO), (0.15, [-85, 70, 40], Q), (0.25, [-85, 70, 40]), (0.35, [-90, -75, -15], C),
                  (0.55, [-70, -60, -10], S), (0.8, ZERO, S)],
    "left_arm": [(0, ZERO), (0.15, [-75, 50, 20], Q), (0.35, [-80, -50, -5], C), (0.8, ZERO, S)],
    "right_leg": [(0, ZERO), (0.15, [-15, 0, 18], Q), (0.6, [-15, 0, 18]), (0.8, ZERO, S)],
    "left_leg": [(0, ZERO), (0.15, [15, 0, -18], Q), (0.6, [15, 0, -18]), (0.8, ZERO, S)],
    "head": [(0, ZERO), (0.35, [0, -20, 0], C), (0.8, ZERO, S)],
})

# IV. Burning Bones, Summer Sun: arms locked out, body spins three full turns
write("form_burning_bones_summer_sun", 1.2, {
    "body": [(0, ZERO), (0.95, [0, -1080, 0], L), (1.2, [0, -1080, 0])],
    "right_arm": [(0, ZERO), (0.1, [-90, -10, 0], Q), (0.95, [-90, -10, 0]), (1.2, ZERO, S)],
    "left_arm": [(0, ZERO), (0.1, [-80, 25, 0], Q), (0.95, [-80, 25, 0]), (1.2, ZERO, S)],
    "torso": [(0, ZERO), (0.1, [10, 0, 0], Q), (0.95, [10, 0, 0]), (1.2, ZERO, S)],
})

# V. Setting Sun Transformation: low forward lean, blade trailing, then a cross-body cut
write("form_setting_sun_transformation", 0.6, {
    "torso": [(0, ZERO), (0.08, [28, 15, 0], Q), (0.22, [28, 15, 0]), (0.3, [15, -35, 0], C), (0.6, ZERO, S)],
    "right_arm": [(0, ZERO), (0.08, [-30, 70, 30], Q), (0.22, [-30, 70, 30]), (0.3, [-95, -60, -20], C),
                  (0.6, ZERO, S)],
    "left_arm": [(0, ZERO), (0.08, [20, 0, -20], Q), (0.3, [-40, -20, -10], C), (0.6, ZERO, S)],
    "right_leg": [(0, ZERO), (0.08, [-45, 0, 0], Q), (0.22, [-45, 0, 0]), (0.6, ZERO, S)],
    "left_leg": [(0, ZERO), (0.08, [35, 0, 0], Q), (0.22, [35, 0, 0]), (0.6, ZERO, S)],
})

# VI. Solar Heat Haze: snap backstep into a guard
write("form_solar_heat_haze", 0.5, {
    "torso": [(0, ZERO), (0.08, [-18, 0, 0], Q), (0.3, [-10, 0, 0], S), (0.5, ZERO, S)],
    "right_arm": [(0, ZERO), (0.08, [-70, 25, 10], Q), (0.35, [-60, 20, 5]), (0.5, ZERO, S)],
    "left_arm": [(0, ZERO), (0.08, [-55, -30, -10], Q), (0.35, [-50, -25, -5]), (0.5, ZERO, S)],
    "right_leg": [(0, ZERO), (0.08, [35, 0, 0], Q), (0.35, [10, 0, 0], S), (0.5, ZERO, S)],
    "left_leg": [(0, ZERO), (0.08, [-30, 0, 0], Q), (0.35, [-5, 0, 0], S), (0.5, ZERO, S)],
})

# VII. Beneficent Radiance: both arms high overhead, then crash straight down
write("form_beneficent_radiance", 0.8, {
    "right_arm": [(0, ZERO), (0.2, [-175, 0, 10], Q), (0.25, [-178, 0, 10]), (0.32, [-35, 0, 0], C),
                  (0.55, [-40, 0, 0], S), (0.8, ZERO, S)],
    "left_arm": [(0, ZERO), (0.2, [-170, 0, -10], Q), (0.25, [-172, 0, -10]), (0.32, [-40, 0, 0], C),
                 (0.55, [-40, 0, 0], S), (0.8, ZERO, S)],
    "torso": [(0, ZERO), (0.2, [-12, 0, 0], Q), (0.32, [30, 0, 0], C), (0.55, [20, 0, 0], S), (0.8, ZERO, S)],
    "right_leg": [(0, ZERO), (0.25, [-40, 0, 0], Q), (0.55, [-20, 0, 0], S), (0.8, ZERO, S)],
    "left_leg": [(0, ZERO), (0.25, [25, 0, 0], Q), (0.55, [10, 0, 0], S), (0.8, ZERO, S)],
})

# VIII. Sunflower Thrust: draw back, then a straight-arm thrust with a deep lunge
write("form_sunflower_thrust", 0.5, {
    "right_arm": [(0, ZERO), (0.12, [-60, 30, 0], Q), (0.18, [-92, 0, 0], C), (0.35, [-90, 0, 0]), (0.5, ZERO, S)],
    "left_arm": [(0, ZERO), (0.12, [-50, -40, 0], Q), (0.18, [-30, -60, -20], C), (0.5, ZERO, S)],
    "torso": [(0, ZERO), (0.12, [0, 25, 0], Q), (0.18, [12, -25, 0], C), (0.5, ZERO, S)],
    "right_leg": [(0, ZERO), (0.18, [-40, 0, 0], C), (0.35, [-40, 0, 0]), (0.5, ZERO, S)],
    "left_leg": [(0, ZERO), (0.18, [30, 0, 0], C), (0.35, [30, 0, 0]), (0.5, ZERO, S)],
})

# IX. Dragon Sun Halo Head Dance: tucked, airborne double spin with the blade out wide
write("form_dragon_sun_halo_head_dance", 1.0, {
    "body": [(0, ZERO), (0.85, [0, -720, 0], L), (1.0, [0, -720, 0])],
    "right_arm": [(0, ZERO), (0.12, [-100, 0, 45], Q), (0.85, [-100, 0, 45]), (1.0, ZERO, S)],
    "left_arm": [(0, ZERO), (0.12, [-30, 0, -50], Q), (0.85, [-30, 0, -50]), (1.0, ZERO, S)],
    "right_leg": [(0, ZERO), (0.12, [-50, 0, 0], Q), (0.85, [-35, 0, 0]), (1.0, ZERO, S)],
    "left_leg": [(0, ZERO), (0.12, [-40, 0, 0], Q), (0.85, [-25, 0, 0]), (1.0, ZERO, S)],
})

# X. Fire Wheel: forward somersault, blade overhead, landing in a crouched slam
write("form_fire_wheel", 0.8, {
    "body": [(0, ZERO), (0.4, [360, 0, 0], L), (0.8, [360, 0, 0])],
    "right_arm": [(0, ZERO), (0.1, [-175, 0, 0], Q), (0.4, [-150, 0, 0]), (0.45, [-40, 0, 0], C), (0.8, ZERO, S)],
    "left_arm": [(0, ZERO), (0.1, [-170, 0, 0], Q), (0.4, [-145, 0, 0]), (0.45, [-40, 0, 0], C), (0.8, ZERO, S)],
    "right_leg": [(0, ZERO), (0.1, [-70, 0, 0], Q), (0.4, [-60, 0, 0]), (0.5, [-30, 0, 0], C), (0.8, ZERO, S)],
    "left_leg": [(0, ZERO), (0.1, [-70, 0, 0], Q), (0.4, [-60, 0, 0]), (0.5, [20, 0, 0], C), (0.8, ZERO, S)],
})

# XI. Fake Rainbow: six alternating diagonal cuts with the torso weaving
ra, la, to = [(0, ZERO)], [(0, ZERO)], [(0, ZERO)]
for i, t in enumerate([0.1, 0.25, 0.4, 0.55, 0.7, 0.85]):
    if i % 2 == 0:
        ra.append((t, [-150, 30, 45], C)); la.append((t, [-120, 10, 10], C)); to.append((t, [0, 30, 8], C))
    else:
        ra.append((t, [-55, -45, -35], C)); la.append((t, [-45, -40, 20], C)); to.append((t, [0, -30, -8], C))
ra.append((1.0, ZERO, S)); la.append((1.0, ZERO, S)); to.append((1.0, ZERO, S))
write("form_fake_rainbow", 1.0, {"right_arm": ra, "left_arm": la, "torso": to})

# XII. Flame Dance finale: gather, spin once, then raise the blade to the sky and bring it down
write("form_flame_dance", 0.6, {
    "body": [(0, ZERO), (0.25, [0, -360, 0], Q), (0.6, [0, -360, 0])],
    "right_arm": [(0, ZERO), (0.25, [-180, 0, 15], Q), (0.32, [-180, 0, 15]), (0.4, [-30, 0, 0], C), (0.6, ZERO, S)],
    "left_arm": [(0, ZERO), (0.25, [-175, 0, -15], Q), (0.32, [-175, 0, -15]), (0.4, [-35, 0, 0], C), (0.6, ZERO, S)],
    "torso": [(0, ZERO), (0.25, [-15, 0, 0], Q), (0.4, [30, 0, 0], C), (0.6, ZERO, S)],
    "right_leg": [(0, ZERO), (0.4, [-30, 0, 15], C), (0.6, ZERO, S)],
    "left_leg": [(0, ZERO), (0.4, [20, 0, -15], C), (0.6, ZERO, S)],
})
