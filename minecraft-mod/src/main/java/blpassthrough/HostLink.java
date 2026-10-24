package blpassthrough;

import blpassthrough.shm.Protocol;
import blpassthrough.shm.SharedMemory;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

public final class HostLink {
	private static final long STALE_NANOS = 500_000_000L;

	private final SharedMemory memory;
	private final MemorySegment scratch = Arena.global().allocate(Protocol.H_END);
	private HostState latest;
	private long lastFrame = -1;
	private long lastChangeNanos;
	private volatile boolean stalled;

	public HostLink() {
		this.memory = SharedMemory.createOrOpen(Protocol.HOST_NAME, Protocol.HOST_SIZE);
	}

	public HostState poll() {
		MemorySegment s = this.memory.segment();
		for (int attempt = 0; attempt < 4; attempt++) {
			int before = s.get(ValueLayout.JAVA_INT, Protocol.H_SEQ);
			VarHandle.acquireFence();
			if ((before & 1) != 0) {
				Thread.onSpinWait();
				continue;
			}

			MemorySegment.copy(s, 0, this.scratch, 0, Protocol.H_END);
			VarHandle.acquireFence();
			int after = s.get(ValueLayout.JAVA_INT, Protocol.H_SEQ);
			if (before != after) {
				continue;
			}

			if (this.scratch.get(ValueLayout.JAVA_INT, Protocol.H_MAGIC) != Protocol.HOST_MAGIC
				|| this.scratch.get(ValueLayout.JAVA_INT, Protocol.H_VERSION) != Protocol.HOST_VERSION) {
				this.latest = null;
				this.stalled = false;
				return null;
			}

			long frame = this.scratch.get(ValueLayout.JAVA_LONG, Protocol.H_HOST_FRAME);
			long now = System.nanoTime();
			if (frame != this.lastFrame) {
				this.lastFrame = frame;
				this.lastChangeNanos = now;
				this.latest = new HostState(this.scratch);
				this.stalled = false;
			} else if (now - this.lastChangeNanos > STALE_NANOS) {
				if (this.latest != null) {
					this.stalled = this.latest.has(Protocol.FLAG_MISSION);
				}
				this.latest = null;
			}

			break;
		}

		return this.latest != null && this.latest.has(Protocol.FLAG_MISSION) ? this.latest : null;
	}

	public boolean stalled() {
		return this.stalled;
	}
}
