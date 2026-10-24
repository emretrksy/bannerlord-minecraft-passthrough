import argparse
import struct

import numpy as np
from PIL import Image

import blmc


def main():
    p = argparse.ArgumentParser()
    p.add_argument("out", help="output prefix, e.g. shots/frame")
    args = p.parse_args()

    frame = blmc.read_latest_frame(blmc.open_frame())
    if frame is None:
        raise SystemExit("no frame published yet")
    w, h = frame["width"], frame["height"]
    color = np.frombuffer(frame["color"], np.uint8).reshape(h, w, 4)
    depth = np.frombuffer(frame["depth"], np.float32).reshape(h, w)
    if frame["flags"] & 1:
        color, depth = color[::-1], depth[::-1]
    filled = depth != frame["empty_depth"]
    ndc = depth if frame["flags"] & 2 else depth * 2 - 1
    dist = np.where(filled, frame["m32"] / (ndc + frame["m22"]), 0)
    print({k: v for k, v in frame.items() if k not in ("color", "depth", "overlay")})
    if frame["overlay"] is not None:
        overlay = np.frombuffer(frame["overlay"], np.uint8).reshape(h, w, 4)
        Image.fromarray((overlay[::-1] if frame["flags"] & 1 else overlay).copy(), "RGBA").save(args.out + "_hud.png")
    print("filled pixels:", int(filled.sum()), "distance range:", float(dist[filled].min()) if filled.any() else None,
          float(dist[filled].max()) if filled.any() else None)
    Image.fromarray(color[..., :3].copy()).save(args.out + "_color.png")
    vis = np.where(filled, 255 - np.clip(dist * 25, 0, 255), 0).astype(np.uint8)
    Image.fromarray(vis).save(args.out + "_depth.png")


if __name__ == "__main__":
    main()
