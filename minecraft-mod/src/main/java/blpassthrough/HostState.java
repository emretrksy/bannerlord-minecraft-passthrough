package blpassthrough;

import blpassthrough.shm.Protocol;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class HostState {
	public final int flags;
	public final long hostFrame;
	public final int missionId;
	public final float aspect;
	public final Vector3d playerPos;
	public final Vector3f lookDir;
	public final int teleportCount;
	public final Vector3d teleportTarget;
	public final int groundVersion;
	public final int groundOriginX;
	public final int groundOriginY;
	public final float[] ground;
	public final List<Agent> agents;
	public final int hurtCount;
	public final Hurt[] hurts;
	public final int lootCount;
	public final Loot[] loots;
	public final Input input;

	HostState(MemorySegment s) {
		this.flags = s.get(ValueLayout.JAVA_INT_UNALIGNED, Protocol.H_FLAGS);
		this.hostFrame = s.get(ValueLayout.JAVA_LONG_UNALIGNED, Protocol.H_HOST_FRAME);
		this.missionId = s.get(ValueLayout.JAVA_INT_UNALIGNED, Protocol.H_MISSION_ID);
		this.aspect = s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, Protocol.H_ASPECT);
		this.playerPos = vec3d(s, Protocol.H_PLAYER_POS);
		this.lookDir = new Vector3f(s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, Protocol.H_LOOK_DIR),
			s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, Protocol.H_LOOK_DIR + 4), s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, Protocol.H_LOOK_DIR + 8));
		this.teleportCount = s.get(ValueLayout.JAVA_INT_UNALIGNED, Protocol.H_TELEPORT_COUNT);
		this.teleportTarget = vec3d(s, Protocol.H_TELEPORT_TARGET);
		this.groundVersion = s.get(ValueLayout.JAVA_INT_UNALIGNED, Protocol.H_GROUND_VERSION);
		this.groundOriginX = s.get(ValueLayout.JAVA_INT_UNALIGNED, Protocol.H_GROUND_ORIGIN_X);
		this.groundOriginY = s.get(ValueLayout.JAVA_INT_UNALIGNED, Protocol.H_GROUND_ORIGIN_Y);
		this.ground = s.asSlice(Protocol.H_GROUND, (long) Protocol.GROUND_SIZE * Protocol.GROUND_SIZE * 4)
			.toArray(ValueLayout.JAVA_FLOAT_UNALIGNED);

		int count = Math.min(s.get(ValueLayout.JAVA_INT_UNALIGNED, Protocol.H_AGENT_COUNT), Protocol.MAX_AGENTS);
		this.agents = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			long o = Protocol.H_AGENTS + (long) i * Protocol.AGENT_SIZE;
			this.agents.add(new Agent(
				s.get(ValueLayout.JAVA_INT_UNALIGNED, o),
				s.get(ValueLayout.JAVA_INT_UNALIGNED, o + 4),
				new Vector3d(
					s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, o + 8),
					s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, o + 12),
					s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, o + 16)),
				new Vector3f(s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, o + 20), s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, o + 24), 0.0F)));
		}

		this.hurtCount = s.get(ValueLayout.JAVA_INT_UNALIGNED, Protocol.H_HURT_COUNT);
		this.hurts = new Hurt[Protocol.HURT_CAPACITY];
		for (int i = 0; i < Protocol.HURT_CAPACITY; i++) {
			long o = Protocol.H_HURTS + (long) i * Protocol.HURT_SIZE;
			this.hurts[i] = new Hurt(s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, o), s.get(ValueLayout.JAVA_INT_UNALIGNED, o + 4),
				s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, o + 8), s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, o + 12));
		}

		this.lootCount = s.get(ValueLayout.JAVA_INT_UNALIGNED, Protocol.H_LOOT_COUNT);
		this.loots = new Loot[Protocol.LOOT_CAPACITY];
		for (int i = 0; i < Protocol.LOOT_CAPACITY; i++) {
			long o = Protocol.H_LOOTS + (long) i * Protocol.LOOT_SIZE;
			this.loots[i] = new Loot(
				new Vector3d(
					s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, o),
					s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, o + 4),
					s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, o + 8)),
				Byte.toUnsignedInt(s.get(ValueLayout.JAVA_BYTE, o + 12)),
				Byte.toUnsignedInt(s.get(ValueLayout.JAVA_BYTE, o + 13)),
				Byte.toUnsignedInt(s.get(ValueLayout.JAVA_BYTE, o + 14)));
		}

		long in = Protocol.H_INPUT;
		int[] presses = new int[Protocol.KEY_COUNT];
		for (int i = 0; i < Protocol.KEY_COUNT; i++) {
			presses[i] = s.get(ValueLayout.JAVA_INT_UNALIGNED, in + Protocol.I_PRESSES + i * 4L);
		}
		int[] typed = new int[Protocol.TYPED_CAPACITY];
		for (int i = 0; i < Protocol.TYPED_CAPACITY; i++) {
			typed[i] = s.get(ValueLayout.JAVA_INT_UNALIGNED, in + Protocol.I_TYPED + i * 4L);
		}
		this.input = new Input(
			s.get(ValueLayout.JAVA_INT_UNALIGNED, in + Protocol.I_FLAGS),
			s.get(ValueLayout.JAVA_INT_UNALIGNED, in + Protocol.I_KEYS_DOWN),
			presses,
			s.get(ValueLayout.JAVA_DOUBLE_UNALIGNED, in + Protocol.I_MOUSE_X),
			s.get(ValueLayout.JAVA_DOUBLE_UNALIGNED, in + Protocol.I_MOUSE_Y),
			s.get(ValueLayout.JAVA_DOUBLE_UNALIGNED, in + Protocol.I_SCROLL),
			s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, in + Protocol.I_CURSOR_X),
			s.get(ValueLayout.JAVA_FLOAT_UNALIGNED, in + Protocol.I_CURSOR_Y),
			s.get(ValueLayout.JAVA_INT_UNALIGNED, in + Protocol.I_TYPED_COUNT),
			typed);
	}

	public boolean has(int flag) {
		return (this.flags & flag) != 0;
	}

	public Hurt hurt(int number) {
		return this.hurts[Math.floorMod(number, Protocol.HURT_CAPACITY)];
	}

	private static Vector3d vec3d(MemorySegment s, long offset) {
		return new Vector3d(
			s.get(ValueLayout.JAVA_DOUBLE_UNALIGNED, offset),
			s.get(ValueLayout.JAVA_DOUBLE_UNALIGNED, offset + 8),
			s.get(ValueLayout.JAVA_DOUBLE_UNALIGNED, offset + 16));
	}

	public record Agent(int index, int flags, Vector3d position, Vector3f facing) {
		public boolean has(int flag) {
			return (this.flags & flag) != 0;
		}
	}

	public Loot loot(int number) {
		return this.loots[Math.floorMod(number, Protocol.LOOT_CAPACITY)];
	}

	public record Loot(Vector3d position, int tier, int weapon, int flags) {
	}

	public record Hurt(float damage, int attacker, float fromX, float fromY) {
	}

	public record Input(int flags, int keysDown, int[] presses, double mouseX, double mouseY, double scroll, float cursorX, float cursorY,
		int typedCount, int[] typed) {
		public boolean has(int flag) {
			return (this.flags & flag) != 0;
		}

		public int typed(int number) {
			return this.typed[Math.floorMod(number, Protocol.TYPED_CAPACITY)];
		}

		public boolean down(int key) {
			return (this.keysDown & (1 << key)) != 0;
		}
	}
}
