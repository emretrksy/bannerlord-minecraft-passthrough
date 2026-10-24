package blpassthrough.shm;

public final class Protocol {
	private Protocol() {
	}

	public static final String HOST_NAME = "Local\\BLMC_Host";
	public static final long HOST_SIZE = 16384;
	public static final int HOST_MAGIC = 0x434D4C42;
	public static final int HOST_VERSION = 3;

	public static final long H_MAGIC = 0;
	public static final long H_VERSION = 4;
	public static final long H_SEQ = 8;
	public static final long H_FLAGS = 12;
	public static final long H_HOST_FRAME = 16;
	public static final long H_TIME = 24;
	public static final long H_CAM_POS = 32;
	public static final long H_CAM_RIGHT = 56;
	public static final long H_CAM_UP = 68;
	public static final long H_CAM_FWD = 80;
	public static final long H_VFOV = 92;
	public static final long H_ASPECT = 96;
	public static final long H_NEAR = 100;
	public static final long H_FAR = 104;
	public static final long H_SCREEN_W = 108;
	public static final long H_SCREEN_H = 112;
	public static final long H_MISSION_ID = 116;
	public static final long H_PLAYER_POS = 120;
	public static final long H_BODY_FWD = 144;
	public static final long H_LOOK_DIR = 156;
	public static final long H_VELOCITY = 168;
	public static final long H_HEALTH = 180;
	public static final long H_HEALTH_MAX = 184;
	public static final long H_TELEPORT_COUNT = 188;
	public static final long H_TELEPORT_TARGET = 192;
	public static final long H_GROUND_VERSION = 220;
	public static final long H_GROUND_ORIGIN_X = 224;
	public static final long H_GROUND_ORIGIN_Y = 228;
	public static final long H_GROUND_SIZE = 232;
	public static final long H_GROUND = 256;
	public static final int GROUND_SIZE = 49;
	public static final long H_AGENT_COUNT = 10240;
	public static final long H_AGENTS = 10256;
	public static final int AGENT_SIZE = 32;
	public static final int MAX_AGENTS = 128;
	public static final int AGENT_ENEMY = 1;
	public static final int AGENT_RIDER = 4;
	public static final int AGENT_MOUNT = 8;
	public static final long H_HURT_COUNT = 14400;
	public static final long H_HURTS = 14416;
	public static final int HURT_SIZE = 16;
	public static final int HURT_CAPACITY = 16;
	public static final long H_INPUT = 14848;
	public static final long I_FLAGS = 0;
	public static final long I_KEYS_DOWN = 4;
	public static final long I_PRESSES = 8;
	public static final long I_MOUSE_X = 104;
	public static final long I_MOUSE_Y = 112;
	public static final long I_SCROLL = 120;
	public static final long I_CURSOR_X = 128;
	public static final long I_CURSOR_Y = 132;
	public static final int KEY_COUNT = 24;
	public static final long I_TYPED_COUNT = 136;
	public static final long I_TYPED = 140;
	public static final int TYPED_CAPACITY = 16;
	public static final int TYPED_BACKSPACE = 8;
	public static final int TYPED_ENTER = 13;
	public static final int TYPED_ESCAPE = 27;
	public static final int INPUT_CONTROLLING = 1;
	public static final int INPUT_CURSOR = 2;
	public static final long H_LOOT_COUNT = 15104;
	public static final long H_LOOTS = 15120;
	public static final int LOOT_SIZE = 16;
	public static final int LOOT_CAPACITY = 32;
	public static final long H_END = H_LOOTS + (long) LOOT_SIZE * LOOT_CAPACITY;

	public static final int FLAG_MISSION = 1;
	public static final int FLAG_PLAYER = 2;
	public static final int FLAG_MOUNTED = 8;
	public static final int FLAG_BANNERLORD_UI = 16;
	public static final int FLAG_MENU = 32;
	public static final int FLAG_TURKISH = 64;
	public static final int FLAG_PAUSED = 128;

	public static final String EVENTS_NAME = "Local\\BLMC_Events";
	public static final long EVENTS_SIZE = 16384;
	public static final int EVENTS_MAGIC = 0x454D4C42;
	public static final long E_MAGIC = 0;
	public static final long E_VERSION = 4;
	public static final long E_WRITE_COUNT = 8;
	public static final long E_EVENTS = 64;
	public static final int EVENT_SIZE = 32;
	public static final int EVENT_CAPACITY = 256;
	public static final int EVENT_EXPLOSION = 1;
	public static final int EVENT_AGENT_HIT = 4;
	public static final int EVENT_PLAYER_DIED = 5;

	public static final String PLAYER_NAME = "Local\\BLMC_Player";
	public static final long PLAYER_SIZE = 4096;
	public static final int PLAYER_MAGIC = 0x504D4C42;
	public static final long P_MAGIC = 0;
	public static final long P_VERSION = 4;
	public static final long P_SEQ = 8;
	public static final long P_FLAGS = 12;
	public static final long P_FRAME = 16;
	public static final long P_CAM_POS = 24;
	public static final long P_CAM_RIGHT = 48;
	public static final long P_CAM_UP = 60;
	public static final long P_CAM_FWD = 72;
	public static final long P_VFOV = 84;
	public static final long P_MISSION_ID = 88;
	public static final long P_FEET = 96;
	public static final long P_BODY_FWD = 120;
	public static final long P_FOCUS = 136;
	public static final int PLAYER_VALID = 1;
	public static final int PLAYER_FIRST_PERSON = 2;
	public static final int PLAYER_DEAD = 4;
	public static final int PLAYER_CURSOR = 8;
	public static final int PLAYER_FOCUS = 16;
	public static final int PLAYER_MOUNTED = 32;
	public static final int PLAYER_PAUSED = 64;

	public static final String FRAME_NAME = "Local\\BLMC_Frame";
	public static final int FRAME_MAGIC = 0x464D4C42;
	public static final int FRAME_VERSION = 2;
	public static final int SLOT_COUNT = 3;
	public static final int MAX_WIDTH = 1920;
	public static final int MAX_HEIGHT = 1200;
	public static final long SLOTS_OFFSET = 4096;
	public static final long SLOT_HEADER = 256;
	public static final long SLOT_STRIDE = align(SLOT_HEADER + (long) MAX_WIDTH * MAX_HEIGHT * 12, 4096);
	public static final long FRAME_SIZE = SLOTS_OFFSET + SLOT_STRIDE * SLOT_COUNT;

	public static final long F_MAGIC = 0;
	public static final long F_VERSION = 4;
	public static final long F_LATEST_SLOT = 8;
	public static final long F_SLOT_COUNT = 12;
	public static final long F_SLOT_STRIDE = 16;
	public static final long F_MAX_WIDTH = 24;
	public static final long F_MAX_HEIGHT = 28;
	public static final long F_FRAMES_WRITTEN = 32;

	public static final long S_SEQ = 0;
	public static final long S_WIDTH = 4;
	public static final long S_HEIGHT = 8;
	public static final long S_FLAGS = 12;
	public static final long S_HOST_FRAME = 16;
	public static final long S_CAM_POS = 24;
	public static final long S_CAM_RIGHT = 48;
	public static final long S_CAM_UP = 60;
	public static final long S_CAM_FWD = 72;
	public static final long S_TAN_HALF_FOV_Y = 84;
	public static final long S_ASPECT = 88;
	public static final long S_M22 = 92;
	public static final long S_M32 = 96;
	public static final long S_EMPTY_DEPTH = 100;
	public static final long S_COLOR_OFFSET = 104;
	public static final long S_DEPTH_OFFSET = 108;
	public static final long S_OVERLAY_OFFSET = 112;

	public static final int SLOT_ROWS_BOTTOM_UP = 1;
	public static final int SLOT_Z_ZERO_TO_ONE = 2;
	public static final int SLOT_HAS_OVERLAY = 4;

	public static final int NO_SLOT = -1;

	private static long align(long value, long alignment) {
		return (value + alignment - 1) / alignment * alignment;
	}
}
