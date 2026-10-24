import mmap
import struct

HOST_NAME = "Local\\BLMC_Host"
HOST_SIZE = 16384
HOST_MAGIC = 0x434D4C42
FRAME_NAME = "Local\\BLMC_Frame"
FRAME_MAGIC = 0x464D4C42
MAX_WIDTH, MAX_HEIGHT, SLOT_COUNT = 1920, 1200, 3
SLOTS_OFFSET, SLOT_HEADER = 4096, 256
SLOT_STRIDE = (SLOT_HEADER + MAX_WIDTH * MAX_HEIGHT * 12 + 4095) // 4096 * 4096
FRAME_SIZE = SLOTS_OFFSET + SLOT_STRIDE * SLOT_COUNT

FLAG_MISSION, FLAG_PLAYER, FLAG_CROUCH, FLAG_MOUNTED, FLAG_FIRST_PERSON = 1, 2, 4, 8, 16

HOST_BODY = struct.Struct("<IQd3d3f3f3f4f3I3d3f3f3f2f2I2i")
SLOT_HEAD = struct.Struct("<4IQ3d3f3f3f5f3I")


def open_host():
    return mmap.mmap(-1, HOST_SIZE, tagname=HOST_NAME)


def open_frame():
    return mmap.mmap(-1, FRAME_SIZE, tagname=FRAME_NAME)


def write_host(m, seq, body):
    struct.pack_into("<I", m, 8, seq | 1)
    HOST_BODY.pack_into(m, 12, *body)
    struct.pack_into("<II", m, 0, HOST_MAGIC, 3)
    struct.pack_into("<I", m, 8, (seq | 1) + 1)
    return (seq | 1) + 1


def read_latest_frame(m):
    magic, = struct.unpack_from("<I", m, 0)
    if magic != FRAME_MAGIC:
        return None
    slot, = struct.unpack_from("<i", m, 8)
    if slot < 0:
        return None
    base = SLOTS_OFFSET + slot * SLOT_STRIDE
    head = SLOT_HEAD.unpack_from(m, base)
    seq, width, height, flags, host_frame = head[:5]
    color_off, depth_off, overlay_off = head[-3:]
    n = width * height * 4
    color = bytes(m[base + color_off: base + color_off + n])
    depth = bytes(m[base + depth_off: base + depth_off + n])
    return {"seq": seq, "width": width, "height": height, "flags": flags, "host_frame": host_frame,
            "cam_pos": head[5:8], "right": head[8:11], "up": head[11:14], "fwd": head[14:17],
            "tan_half_fov_y": head[17], "aspect": head[18], "m22": head[19], "m32": head[20],
            "empty_depth": head[21], "color": color, "depth": depth,
            "overlay": bytes(m[base + overlay_off: base + overlay_off + n]) if flags & 4 else None}
