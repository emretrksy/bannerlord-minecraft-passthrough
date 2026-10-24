import argparse
import math
import time

import blmc


def look_at(eye, target):
    fx, fy, fz = (target[i] - eye[i] for i in range(3))
    n = math.sqrt(fx * fx + fy * fy + fz * fz)
    fwd = (fx / n, fy / n, fz / n)
    rx, ry, rz = fwd[1], -fwd[0], 0.0
    rn = math.hypot(rx, ry) or 1.0
    right = (rx / rn, ry / rn, rz)
    up = (right[1] * fwd[2] - right[2] * fwd[1], right[2] * fwd[0] - right[0] * fwd[2], right[0] * fwd[1] - right[1] * fwd[0])
    return right, up, fwd


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--seconds", type=float, default=0, help="0 runs until interrupted")
    p.add_argument("--still", action="store_true", help="keep the player and camera still")
    args = p.parse_args()

    m = blmc.open_host()
    seq, frame, start = 0, 0, time.time()
    centre = (500.0, 400.0, 20.0)
    while args.seconds == 0 or time.time() - start < args.seconds:
        t = 0.0 if args.still else time.time() - start
        a = t * 0.5
        player = (centre[0] + 3 * math.cos(a), centre[1] + 3 * math.sin(a), centre[2])
        body = (-math.sin(a), math.cos(a), 0.0) if not args.still else (0.0, 1.0, 0.0)
        speed = 0.0 if args.still else 1.5
        vel = (body[0] * speed, body[1] * speed, 0.0)
        eye = (player[0] - 3.5 * body[0], player[1] - 3.5 * body[1], player[2] + 2.2)
        right, up, fwd = look_at(eye, (player[0], player[1], player[2] + 1.2))
        swing = int(t / 1.5)
        body_fields = (
            blmc.FLAG_MISSION | blmc.FLAG_PLAYER, frame, t, *eye, *right, *up, *fwd,
            math.radians(60), 16 / 9, 0.1, 12500.0, 1920, 1080, 1,
            *player, *body, *body, *vel, 80.0, 100.0, swing, 0, 1, 7)
        seq = blmc.write_host(m, seq, body_fields)
        frame += 1
        time.sleep(1 / 120)


if __name__ == "__main__":
    main()
