package blpassthrough;

import blpassthrough.shm.Protocol;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class Coords {
	private static final double ANCHOR_Y = 100.0;

	private int missionId = Integer.MIN_VALUE;
	private volatile double yOffset;

	public void update(HostState host) {
		if (host.missionId != this.missionId && host.has(Protocol.FLAG_PLAYER)) {
			this.missionId = host.missionId;
			this.yOffset = ANCHOR_Y - host.playerPos.z;
		}
	}

	public Vec3 position(Vector3d bl) {
		return new Vec3(bl.x, bl.z + this.yOffset, -bl.y);
	}

	public Vector3d toBannerlord(double x, double y, double z) {
		return new Vector3d(x, -z, y - this.yOffset);
	}

	public double yOffset() {
		return this.yOffset;
	}

	public static Vector3f directionToBannerlord(float x, float y, float z) {
		return new Vector3f(x, -z, y);
	}

	public static Vector3f directionToBannerlord(org.joml.Vector3fc mc) {
		return directionToBannerlord(mc.x(), mc.y(), mc.z());
	}

	public static Vec3 direction(Vector3f bl) {
		return new Vec3(bl.x, bl.z, -bl.y);
	}

	public static float yaw(Vec3 dir) {
		return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
	}

	public static float pitch(Vec3 dir) {
		double length = dir.length();
		return length < 1.0E-6 ? 0.0F : (float) -Math.toDegrees(Math.asin(dir.y / length));
	}
}
