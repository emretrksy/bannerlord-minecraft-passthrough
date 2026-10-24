package blpassthrough;

import blpassthrough.shm.Protocol;
import blpassthrough.shm.SharedMemory;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import net.minecraft.client.renderer.Projection;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class FrameExporter {
	private static final int IN_FLIGHT = 3;
	private static final Vector4f TRANSPARENT = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);

	private SharedMemory memory;
	private final Readback[] ring = new Readback[IN_FLIGHT];
	private Readback current;
	private int nextSlot;
	private long framesWritten;
	private boolean warnedSize;

	public void captureLevel(RenderTarget target, Projection projection, CameraPose pose) {
		this.current = null;
		int width = target.width;
		int height = target.height;
		if (width > Protocol.MAX_WIDTH || height > Protocol.MAX_HEIGHT) {
			if (!this.warnedSize) {
				this.warnedSize = true;
				Passthrough.LOGGER.warn("Minecraft window {}x{} is larger than the {}x{} the frame block holds; not exporting",
					width, height, Protocol.MAX_WIDTH, Protocol.MAX_HEIGHT);
			}
			return;
		}

		if (this.memory == null) {
			this.memory = SharedMemory.createOrOpen(Protocol.FRAME_NAME, Protocol.FRAME_SIZE);
			this.writeHeader();
		}

		Readback readback = this.freeReadback(width, height);
		if (readback == null) {
			return;
		}

		Matrix4f matrix = projection.getMatrix(new Matrix4f());
		readback.pose = pose;
		readback.tanHalfFovY = (float) Math.tan(Math.toRadians(projection.fov()) * 0.5);
		readback.aspect = projection.width() / projection.height();
		readback.m22 = matrix.m22();
		readback.m32 = matrix.m32();
		readback.zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
		readback.hasOverlay = false;
		readback.pending = 2;

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.copyTextureToBuffer(target.getColorTexture(), readback.color, 0L, () -> this.onCopied(readback, true), 0);
		encoder.copyTextureToBuffer(target.getDepthTexture(), readback.depth, 0L, () -> this.onCopied(readback, true), 0);

		encoder.clearColorTexture(target.getColorTexture(), TRANSPARENT);
		this.current = readback;
	}

	public void captureOverlay(RenderTarget target, boolean hudOnly) {
		Readback readback = this.current;
		this.current = null;
		if (readback == null) {
			return;
		}
		if (!hudOnly || target.width != readback.width || target.height != readback.height) {
			if (readback.pending == 0) {
				this.onCopied(readback, false);
			}
			return;
		}

		readback.hasOverlay = true;
		readback.pending++;
		RenderSystem.getDevice().createCommandEncoder()
			.copyTextureToBuffer(target.getColorTexture(), readback.overlay, 0L, () -> this.onCopied(readback, true), 0);
	}

	private Readback freeReadback(int width, int height) {
		for (int i = 0; i < IN_FLIGHT; i++) {
			Readback readback = this.ring[i];
			if (readback != null && readback.pending > 0) {
				continue;
			}

			if (readback == null || readback.width != width || readback.height != height) {
				if (readback != null) {
					readback.close();
				}
				readback = new Readback(width, height);
				this.ring[i] = readback;
			}
			return readback;
		}
		return null;
	}

	private void onCopied(Readback readback, boolean copyLanded) {
		if (copyLanded) {
			readback.pending--;
		}
		if (readback.pending > 0 || readback == this.current) {
			return;
		}

		MemorySegment shm = this.memory.segment();
		int slot = this.nextSlot;
		this.nextSlot = (slot + 1) % Protocol.SLOT_COUNT;
		long base = Protocol.SLOTS_OFFSET + slot * Protocol.SLOT_STRIDE;
		long pixels = (long) readback.width * readback.height * 4;
		long colorOffset = Protocol.SLOT_HEADER;
		long depthOffset = colorOffset + pixels;
		long overlayOffset = depthOffset + pixels;

		int seq = shm.get(ValueLayout.JAVA_INT, base + Protocol.S_SEQ);
		shm.set(ValueLayout.JAVA_INT, base + Protocol.S_SEQ, seq | 1);
		VarHandle.releaseFence();

		copy(readback.color, shm, base + colorOffset, pixels);
		copy(readback.depth, shm, base + depthOffset, pixels);
		if (readback.hasOverlay) {
			copy(readback.overlay, shm, base + overlayOffset, pixels);
		}

		CameraPose pose = readback.pose;
		int flags = Protocol.SLOT_ROWS_BOTTOM_UP
			| (readback.zZeroToOne ? Protocol.SLOT_Z_ZERO_TO_ONE : 0)
			| (readback.hasOverlay ? Protocol.SLOT_HAS_OVERLAY : 0);
		shm.set(ValueLayout.JAVA_INT, base + Protocol.S_WIDTH, readback.width);
		shm.set(ValueLayout.JAVA_INT, base + Protocol.S_HEIGHT, readback.height);
		shm.set(ValueLayout.JAVA_INT, base + Protocol.S_FLAGS, flags);
		shm.set(ValueLayout.JAVA_LONG, base + Protocol.S_HOST_FRAME, pose.frame());
		shm.set(ValueLayout.JAVA_DOUBLE, base + Protocol.S_CAM_POS, pose.position().x);
		shm.set(ValueLayout.JAVA_DOUBLE, base + Protocol.S_CAM_POS + 8, pose.position().y);
		shm.set(ValueLayout.JAVA_DOUBLE, base + Protocol.S_CAM_POS + 16, pose.position().z);
		putVec(shm, base + Protocol.S_CAM_RIGHT, pose.right().x, pose.right().y, pose.right().z);
		putVec(shm, base + Protocol.S_CAM_UP, pose.up().x, pose.up().y, pose.up().z);
		putVec(shm, base + Protocol.S_CAM_FWD, pose.forward().x, pose.forward().y, pose.forward().z);
		shm.set(ValueLayout.JAVA_FLOAT, base + Protocol.S_TAN_HALF_FOV_Y, readback.tanHalfFovY);
		shm.set(ValueLayout.JAVA_FLOAT, base + Protocol.S_ASPECT, readback.aspect);
		shm.set(ValueLayout.JAVA_FLOAT, base + Protocol.S_M22, readback.m22);
		shm.set(ValueLayout.JAVA_FLOAT, base + Protocol.S_M32, readback.m32);
		shm.set(ValueLayout.JAVA_FLOAT, base + Protocol.S_EMPTY_DEPTH, 0.0F);
		shm.set(ValueLayout.JAVA_INT, base + Protocol.S_COLOR_OFFSET, (int) colorOffset);
		shm.set(ValueLayout.JAVA_INT, base + Protocol.S_DEPTH_OFFSET, (int) depthOffset);
		shm.set(ValueLayout.JAVA_INT, base + Protocol.S_OVERLAY_OFFSET, (int) overlayOffset);

		VarHandle.releaseFence();
		shm.set(ValueLayout.JAVA_INT, base + Protocol.S_SEQ, (seq | 1) + 1);
		shm.set(ValueLayout.JAVA_INT, Protocol.F_LATEST_SLOT, slot);
		shm.set(ValueLayout.JAVA_LONG, Protocol.F_FRAMES_WRITTEN, ++this.framesWritten);
	}

	private static void copy(GpuBuffer buffer, MemorySegment shm, long offset, long bytes) {
		try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
			MemorySegment.copy(MemorySegment.ofBuffer(view.data()), 0, shm, offset, bytes);
		}
	}

	private void writeHeader() {
		MemorySegment shm = this.memory.segment();
		shm.set(ValueLayout.JAVA_INT, Protocol.F_LATEST_SLOT, Protocol.NO_SLOT);
		shm.set(ValueLayout.JAVA_INT, Protocol.F_VERSION, Protocol.FRAME_VERSION);
		shm.set(ValueLayout.JAVA_INT, Protocol.F_SLOT_COUNT, Protocol.SLOT_COUNT);
		shm.set(ValueLayout.JAVA_LONG, Protocol.F_SLOT_STRIDE, Protocol.SLOT_STRIDE);
		shm.set(ValueLayout.JAVA_INT, Protocol.F_MAX_WIDTH, Protocol.MAX_WIDTH);
		shm.set(ValueLayout.JAVA_INT, Protocol.F_MAX_HEIGHT, Protocol.MAX_HEIGHT);
		VarHandle.releaseFence();
		shm.set(ValueLayout.JAVA_INT, Protocol.F_MAGIC, Protocol.FRAME_MAGIC);
	}

	private static void putVec(MemorySegment shm, long offset, float x, float y, float z) {
		shm.set(ValueLayout.JAVA_FLOAT, offset, x);
		shm.set(ValueLayout.JAVA_FLOAT, offset + 4, y);
		shm.set(ValueLayout.JAVA_FLOAT, offset + 8, z);
	}

	private static final class Readback {
		final int width;
		final int height;
		final GpuBuffer color;
		final GpuBuffer depth;
		final GpuBuffer overlay;
		int pending;
		boolean hasOverlay;
		CameraPose pose;
		float tanHalfFovY;
		float aspect;
		float m22;
		float m32;
		boolean zZeroToOne;

		Readback(int width, int height) {
			this.width = width;
			this.height = height;
			GpuDevice device = RenderSystem.getDevice();
			long size = (long) width * height * 4;
			int usage = GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST;
			this.color = device.createBuffer(() -> "Passthrough colour readback", usage, size);
			this.depth = device.createBuffer(() -> "Passthrough depth readback", usage, size);
			this.overlay = device.createBuffer(() -> "Passthrough HUD readback", usage, size);
		}

		void close() {
			this.color.close();
			this.depth.close();
			this.overlay.close();
		}
	}
}
