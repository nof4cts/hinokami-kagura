"""
Writes the player animations (Blockbench / GeckoLib JSON, read by Player Animator) for every move.

    python3 tools/gen_anims.py

Keyframes are (time s, [x, y, z] degrees, easing). Conventions as vanilla: x -90 = arm straight forward,
y + on the left arm / - on the right arm swings it inward across the chest.
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "shrine", "player_animation")
Z = [0, 0, 0]


def track(keys):
    return {"rotation": {f"{t:.3f}".rstrip("0").rstrip(".") if t else "0.0": ({"vector": v, "easing": e} if e else {"vector": v})
                         for t, v, e in keys}}


def write(name, length, bones):
    data = {"format_version": "1.8.0", "animations": {name: {"animation_length": length,
                                                              "bones": {b: track(k) for b, k in bones.items()}}}}
    with open(os.path.join(OUT, name + ".json"), "w") as f:
        json.dump(data, f, indent=1)
    print("wrote", name)


def main():
    os.makedirs(OUT, exist_ok=True)
    # Dismantle: a wrist flick across, fast out, slow back
    write("move_dismantle", 0.55, {
        "right_arm": [(0, Z, None), (0.07, [-105, -45, 0], "easeOutQuad"), (0.15, [-75, 55, 0], "easeOutCubic"),
                      (0.3, [-65, 45, 0], "easeInOutSine"), (0.55, Z, "easeInOutSine")],
        "torso": [(0, Z, None), (0.07, [0, 14, 0], "easeOutQuad"), (0.15, [0, -18, 0], "easeOutCubic"), (0.55, Z, "easeInOutSine")],
    })
    # Barrage: both hands flicking in turn, a blur
    ra, la, to = [(0, Z, None)], [(0, Z, None)], [(0, Z, None)]
    t = 0.1
    i = 0
    while t < 1.75:
        r = i % 2 == 0
        ra.append((t, [-110, -40, 0] if r else [-70, 50, 0], "easeOutCubic"))
        la.append((t, [-70, -50, 0] if r else [-110, 40, 0], "easeOutCubic"))
        to.append((t, [0, -12 if r else 12, 0], "easeOutCubic"))
        t += 0.1
        i += 1
    ra.append((2.0, Z, "easeInOutSine"))
    la.append((2.0, Z, "easeInOutSine"))
    to.append((2.0, Z, "easeInOutSine"))
    write("move_barrage", 2.0, {"right_arm": ra, "left_arm": la, "torso": to})
    # Cleave: the open palm, straight out
    write("move_cleave", 0.7, {
        "right_arm": [(0, Z, None), (0.08, [-60, 10, 0], "easeInQuad"), (0.15, [-92, 0, 0], "easeOutBack"),
                      (0.45, [-90, 0, 0], None), (0.7, Z, "easeInOutSine")],
        "torso": [(0, Z, None), (0.15, [8, -10, 0], "easeOutCubic"), (0.7, Z, "easeInOutSine")],
    })
    # Spiderweb: hand up, then slammed to the ground, crouched over it
    write("move_spiderweb", 1.1, {
        "right_arm": [(0, Z, None), (0.18, [-170, 0, 10], "easeOutQuad"), (0.33, [-25, 0, 10], "easeInCubic"),
                      (0.75, [-25, 0, 10], None), (1.1, Z, "easeInOutSine")],
        "torso": [(0, Z, None), (0.18, [-10, 0, 0], "easeOutQuad"), (0.33, [38, 0, 0], "easeInCubic"),
                  (0.75, [36, 0, 0], None), (1.1, Z, "easeInOutSine")],
        "head": [(0, Z, None), (0.33, [-25, 0, 0], "easeInCubic"), (0.75, [-25, 0, 0], None), (1.1, Z, "easeInOutSine")],
        "left_arm": [(0, Z, None), (0.33, [-20, 0, -25], "easeInCubic"), (1.1, Z, "easeInOutSine")],
    })
    # Black Flash: coil, then the punch
    write("move_black_flash", 0.7, {
        "right_arm": [(0, Z, None), (0.12, [35, 15, 0], "easeOutQuad"), (0.2, [-97, -5, 0], "easeInCubic"),
                      (0.42, [-95, 0, 0], None), (0.7, Z, "easeInOutSine")],
        "torso": [(0, Z, None), (0.12, [0, 38, 0], "easeOutQuad"), (0.2, [5, -28, 0], "easeInCubic"),
                  (0.42, [5, -25, 0], None), (0.7, Z, "easeInOutSine")],
        "left_arm": [(0, Z, None), (0.12, [-60, 30, 0], "easeOutQuad"), (0.2, [20, 0, 0], "easeInCubic"), (0.7, Z, "easeInOutSine")],
    })
    # Open: the hand opens and fire pours out; the bow; the draw, slow and heavy; release
    write("move_fuga", 5.6, {
        "right_arm": [(0, Z, None), (0.35, [-78, -12, 0], "easeOutCubic"), (1.4, [-82, -12, 0], "easeInOutSine"),
                      (1.8, [-90, -35, 0], "easeInOutCubic"), (2.4, [-92, -40, 0], None),
                      (3.8, [-92, -88, 0], "easeOutQuad"), (4.0, [-92, -90, 0], None),
                      (4.12, [-60, -125, 25], "easeOutCubic"), (4.9, [-55, -110, 20], "easeInOutSine"), (5.6, Z, "easeInOutSine")],
        "left_arm": [(0, Z, None), (1.4, Z, None), (1.8, [-92, 28, 0], "easeInOutCubic"), (4.0, [-92, 30, 0], None),
                     (4.12, [-95, 28, 0], "easeOutCubic"), (4.9, [-88, 25, 0], "easeInOutSine"), (5.6, Z, "easeInOutSine")],
        "torso": [(0, Z, None), (1.4, [0, -5, 0], "easeInOutSine"), (1.8, [0, 38, 0], "easeInOutCubic"),
                  (3.8, [0, 44, 0], "easeOutQuad"), (4.12, [0, 30, 0], "easeOutCubic"), (5.6, Z, "easeInOutSine")],
        "head": [(0, Z, None), (1.8, [0, -38, 0], "easeInOutCubic"), (4.0, [0, -42, 0], None), (5.6, Z, "easeInOutSine")],
    })
    # Domain Expansion: the hand sign, held
    write("move_domain", 7.6, {
        "right_arm": [(0, Z, None), (0.45, [-78, -38, 0], "easeOutBack"), (6.8, [-78, -38, 0], None), (7.6, Z, "easeInOutSine")],
        "left_arm": [(0, Z, None), (0.45, [-78, 38, 0], "easeOutBack"), (6.8, [-78, 38, 0], None), (7.6, Z, "easeInOutSine")],
        "head": [(0, Z, None), (0.45, [12, 0, 0], "easeOutCubic"), (2.6, [16, 0, 0], None), (2.7, [-4, 0, 0], "easeOutCubic"),
                 (6.8, [-4, 0, 0], None), (7.6, Z, "easeInOutSine")],
    })
    # World-Cutting Slash: three signs, the hand raised, the cut
    write("move_world_slash", 5.8, {
        "right_arm": [(0, Z, None), (0.25, [-85, -32, 0], "easeOutBack"), (0.9, [-85, -32, 0], None),
                      (1.05, [-112, -18, 0], "easeOutBack"), (1.8, [-112, -18, 0], None),
                      (1.95, [-68, -48, 0], "easeOutBack"), (2.7, [-68, -48, 0], None),
                      (3.1, [-172, 5, -8], "easeInOutCubic"), (3.85, [-176, 8, -8], None),
                      (3.98, [-25, -5, 0], "easeInExpo"), (4.6, [-22, -5, 0], None), (5.8, Z, "easeInOutSine")],
        "left_arm": [(0, Z, None), (0.25, [-85, 32, 0], "easeOutBack"), (0.9, [-85, 32, 0], None),
                     (1.05, [-62, 42, 0], "easeOutBack"), (1.8, [-62, 42, 0], None),
                     (1.95, [-102, 45, 0], "easeOutBack"), (2.7, [-102, 45, 0], None),
                     (3.1, [-10, 0, -12], "easeInOutCubic"), (5.8, Z, "easeInOutSine")],
        "torso": [(0, Z, None), (2.7, Z, None), (3.1, [-8, 0, 0], "easeInOutCubic"), (3.85, [-10, 0, 0], None),
                  (3.98, [22, 0, 0], "easeInExpo"), (4.6, [20, 0, 0], None), (5.8, Z, "easeInOutSine")],
    })


if __name__ == "__main__":
    main()
