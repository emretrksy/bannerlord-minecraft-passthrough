import argparse
import struct
import time

import numpy as np

import blmc

M22, M32 = 0.0, 0.05
OVERSCAN = 1.1


def boxes(feet):
    x, y, z = feet
    return [
        (np.array([x - 0.3, y - 0.2, z]), np.array([x + 0.3, y + 0.2, z + 1.4]), (60, 160, 170)),
        (np.array([x - 0.25, y - 0.25, z + 1.4]), np.array([x + 0.25, y + 0.25, z + 1.9]), (150, 105, 70)),
    ]


def render(host, width, height):
    cam = np.array(host["cam"])
    right, up, fwd = (np.array(host[k]) for k in ("right", "up", "fwd"))
    tan_y = np.tan(host["vfov"] * 0.5) * OVERSCAN
    aspect = width / height
    xs = (np.arange(width) + 0.5) / width * 2 - 1
    ys = 1 - (np.arange(height) + 0.5) / height * 2
    nx, ny = np.meshgrid(xs * tan_y * aspect, ys * tan_y)
    rays = nx[..., None] * right + ny[..., None] * up + fwd

    dist = np.full((height, width), np.inf)
    color = np.zeros((height, width, 4), np.uint8)
    with np.errstate(divide="ignore", invalid="ignore"):
        for lo, hi, rgb in boxes(host["player"]):
            t0 = (lo - cam) / rays
            t1 = (hi - cam) / rays
            tmin = np.nanmax(np.minimum(t0, t1), axis=-1)
            tmax = np.nanmin(np.maximum(t0, t1), axis=-1)
            hit = (tmax >= tmin) & (tmax > 0) & (tmin < dist)
            face = np.argmax(np.minimum(t0, t1), axis=-1)
            p = cam + rays * tmin[..., None]
            checker = ((np.floor(p[..., 0] * 4) + np.floor(p[..., 1] * 4) + np.floor(p[..., 2] * 4)) % 2) * 25
            shade = np.array([0.8, 0.65, 1.0])[face]
            for c in range(3):
                color[..., c] = np.where(hit, np.clip(rgb[c] * shade + checker, 0, 255), color[..., c])
            color[..., 3] = np.where(hit, 255, color[..., 3])
            dist = np.where(hit, tmin, dist)
    depth = np.where(np.isfinite(dist), M32 / dist, 0.0).astype(np.float32)
    return color, depth, tan_y, aspect


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--width", type=int, default=480)
    p.add_argument("--height", type=int, default=270)
    args = p.parse_args()

    host_map, frame_map = blmc.open_host(), blmc.open_frame()
    struct.pack_into("<IIiIQII", frame_map, 0, blmc.FRAME_MAGIC, 1, -1, blmc.SLOT_COUNT, blmc.SLOT_STRIDE,
                     blmc.MAX_WIDTH, blmc.MAX_HEIGHT)
    written, slot = 0, 0
    while True:
        v = blmc.HOST_BODY.unpack_from(bytes(host_map[:208]), 12)
        if not v[0] & blmc.FLAG_MISSION:
            time.sleep(0.1)
            continue
        host = {"frame": v[1], "cam": v[3:6], "right": v[6:9], "up": v[9:12], "fwd": v[12:15], "vfov": v[15],
                "player": v[22:25]}
        color, depth, tan_y, aspect = render(host, args.width, args.height)

        base = blmc.SLOTS_OFFSET + slot * blmc.SLOT_STRIDE
        seq, = struct.unpack_from("<I", frame_map, base)
        struct.pack_into("<I", frame_map, base, seq | 1)
        n = args.width * args.height * 4
        color_off, depth_off = blmc.SLOT_HEADER, blmc.SLOT_HEADER + n
        frame_map[base + color_off: base + color_off + n] = color.tobytes()
        frame_map[base + depth_off: base + depth_off + n] = depth.tobytes()
        blmc.SLOT_HEAD.pack_into(frame_map, base, seq | 1, args.width, args.height, 2, host["frame"], *host["cam"],
                                 *host["right"], *host["up"], *host["fwd"], tan_y, aspect, M22, M32, 0.0,
                                 color_off, depth_off, 0)
        struct.pack_into("<I", frame_map, base, (seq | 1) + 1)
        struct.pack_into("<i", frame_map, 8, slot)
        written += 1
        struct.pack_into("<Q", frame_map, 32, written)
        slot = (slot + 1) % blmc.SLOT_COUNT


if __name__ == "__main__":
    main()
