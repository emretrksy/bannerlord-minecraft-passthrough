package blpassthrough;

import blpassthrough.shm.Protocol;
import blpassthrough.shm.SharedMemory;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import org.joml.Vector3d;

public final class EventWriter {
	private final SharedMemory memory = SharedMemory.createOrOpen(Protocol.EVENTS_NAME, Protocol.EVENTS_SIZE);
	private long written;

	public EventWriter() {
		MemorySegment s = this.memory.segment();
		this.written = s.get(ValueLayout.JAVA_INT, Protocol.E_MAGIC) == Protocol.EVENTS_MAGIC
			? s.get(ValueLayout.JAVA_LONG, Protocol.E_WRITE_COUNT)
			: 0L;
		s.set(ValueLayout.JAVA_INT, Protocol.E_VERSION, 1);
		VarHandle.releaseFence();
		s.set(ValueLayout.JAVA_INT, Protocol.E_MAGIC, Protocol.EVENTS_MAGIC);
	}

	public synchronized void write(int type, int arg, Vector3d bannerlordPosition, float radius, float power) {
		MemorySegment s = this.memory.segment();
		long offset = Protocol.E_EVENTS + (this.written % Protocol.EVENT_CAPACITY) * Protocol.EVENT_SIZE;
		s.set(ValueLayout.JAVA_INT, offset, type);
		s.set(ValueLayout.JAVA_INT, offset + 4, arg);
		s.set(ValueLayout.JAVA_FLOAT, offset + 8, (float) bannerlordPosition.x);
		s.set(ValueLayout.JAVA_FLOAT, offset + 12, (float) bannerlordPosition.y);
		s.set(ValueLayout.JAVA_FLOAT, offset + 16, (float) bannerlordPosition.z);
		s.set(ValueLayout.JAVA_FLOAT, offset + 20, radius);
		s.set(ValueLayout.JAVA_FLOAT, offset + 24, power);
		VarHandle.releaseFence();
		s.set(ValueLayout.JAVA_LONG, Protocol.E_WRITE_COUNT, ++this.written);
	}
}
