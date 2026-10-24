package blpassthrough;

import blpassthrough.shm.Protocol;
import blpassthrough.shm.SharedMemory;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class PlayerPublisher {
	private static final double OVERSCAN = 1.05;

	private final SharedMemory memory = SharedMemory.createOrOpen(Protocol.PLAYER_NAME, Protocol.PLAYER_SIZE);
	private int seq;

	public PlayerPublisher() {
		this.memory.segment().set(ValueLayout.JAVA_INT, Protocol.P_VERSION, 1);
		VarHandle.releaseFence();
		this.memory.segment().set(ValueLayout.JAVA_INT, Protocol.P_MAGIC, Protocol.PLAYER_MAGIC);
	}

	public void publish(int flags, CameraPose camera, int missionId, Vector3d feet, Vector3f bodyForward, Vector3d focus) {
		if (focus != null) {
			flags |= Protocol.PLAYER_FOCUS;
		}
		MemorySegment s = this.memory.segment();
		this.seq |= 1;
		s.set(ValueLayout.JAVA_INT, Protocol.P_SEQ, this.seq);
		VarHandle.releaseFence();

		s.set(ValueLayout.JAVA_INT, Protocol.P_FLAGS, flags);
		s.set(ValueLayout.JAVA_LONG, Protocol.P_FRAME, camera.frame());
		putVec3d(s, Protocol.P_CAM_POS, camera.position());
		putVec3f(s, Protocol.P_CAM_RIGHT, camera.right());
		putVec3f(s, Protocol.P_CAM_UP, camera.up());
		putVec3f(s, Protocol.P_CAM_FWD, camera.forward());
		float tanHalf = (float) (Math.tan(camera.verticalFov() * 0.5) / OVERSCAN);
		s.set(ValueLayout.JAVA_FLOAT, Protocol.P_VFOV, (float) (2.0 * Math.atan(tanHalf)));
		s.set(ValueLayout.JAVA_INT, Protocol.P_MISSION_ID, missionId);
		putVec3d(s, Protocol.P_FEET, feet);
		putVec3f(s, Protocol.P_BODY_FWD, bodyForward);
		if (focus != null) {
			putVec3d(s, Protocol.P_FOCUS, focus);
		}

		VarHandle.releaseFence();
		this.seq++;
		s.set(ValueLayout.JAVA_INT, Protocol.P_SEQ, this.seq);
	}

	public void publishIdle() {
		MemorySegment s = this.memory.segment();
		this.seq |= 1;
		s.set(ValueLayout.JAVA_INT, Protocol.P_SEQ, this.seq);
		VarHandle.releaseFence();
		s.set(ValueLayout.JAVA_INT, Protocol.P_FLAGS, 0);
		s.set(ValueLayout.JAVA_LONG, Protocol.P_FRAME, s.get(ValueLayout.JAVA_LONG, Protocol.P_FRAME) + 1);
		VarHandle.releaseFence();
		this.seq++;
		s.set(ValueLayout.JAVA_INT, Protocol.P_SEQ, this.seq);
	}

	private static void putVec3d(MemorySegment s, long offset, Vector3d v) {
		s.set(ValueLayout.JAVA_DOUBLE, offset, v.x);
		s.set(ValueLayout.JAVA_DOUBLE, offset + 8, v.y);
		s.set(ValueLayout.JAVA_DOUBLE, offset + 16, v.z);
	}

	private static void putVec3f(MemorySegment s, long offset, Vector3f v) {
		s.set(ValueLayout.JAVA_FLOAT, offset, v.x);
		s.set(ValueLayout.JAVA_FLOAT, offset + 4, v.y);
		s.set(ValueLayout.JAVA_FLOAT, offset + 8, v.z);
	}
}
