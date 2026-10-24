package blpassthrough;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public record CameraPose(long frame, Vector3d position, Vector3f right, Vector3f up, Vector3f forward, float verticalFov) {
	public static CameraPose of(Camera camera, Coords coords, long frame) {
		Vec3 p = camera.position();
		Vector3fc left = camera.leftVector();
		return new CameraPose(
			frame,
			coords.toBannerlord(p.x, p.y, p.z),
			Coords.directionToBannerlord(-left.x(), -left.y(), -left.z()),
			Coords.directionToBannerlord(camera.upVector()),
			Coords.directionToBannerlord(camera.forwardVector()),
			(float) Math.toRadians(camera.getFov()));
	}
}
