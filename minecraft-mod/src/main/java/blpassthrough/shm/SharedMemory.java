package blpassthrough.shm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

public final class SharedMemory implements AutoCloseable {
	private static final int PAGE_READWRITE = 0x04;
	private static final int FILE_MAP_ALL_ACCESS = 0xF001F;
	private static final MemorySegment INVALID_HANDLE_VALUE = MemorySegment.ofAddress(-1L);

	private static final MethodHandle CREATE_FILE_MAPPING;
	private static final MethodHandle MAP_VIEW_OF_FILE;
	private static final MethodHandle UNMAP_VIEW_OF_FILE;
	private static final MethodHandle CLOSE_HANDLE;

	static {
		Linker linker = Linker.nativeLinker();
		SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
		CREATE_FILE_MAPPING = linker.downcallHandle(
			kernel32.findOrThrow("CreateFileMappingW"),
			FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
				ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
		MAP_VIEW_OF_FILE = linker.downcallHandle(
			kernel32.findOrThrow("MapViewOfFile"),
			FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
				ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
		UNMAP_VIEW_OF_FILE = linker.downcallHandle(
			kernel32.findOrThrow("UnmapViewOfFile"), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
		CLOSE_HANDLE = linker.downcallHandle(
			kernel32.findOrThrow("CloseHandle"), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	}

	private final MemorySegment handle;
	private final MemorySegment view;

	private SharedMemory(MemorySegment handle, MemorySegment view) {
		this.handle = handle;
		this.view = view;
	}

	public static SharedMemory createOrOpen(String name, long size) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment wideName = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
			MemorySegment handle = (MemorySegment) CREATE_FILE_MAPPING.invokeExact(
				INVALID_HANDLE_VALUE, MemorySegment.NULL, PAGE_READWRITE, (int) (size >>> 32), (int) size, wideName);
			if (handle.address() == 0) {
				throw new IllegalStateException("CreateFileMappingW failed for " + name);
			}

			MemorySegment view = (MemorySegment) MAP_VIEW_OF_FILE.invokeExact(handle, FILE_MAP_ALL_ACCESS, 0, 0, size);
			if (view.address() == 0) {
				int ignored = (int) CLOSE_HANDLE.invokeExact(handle);
				throw new IllegalStateException("MapViewOfFile failed for " + name);
			}

			return new SharedMemory(handle, view.reinterpret(size));
		} catch (RuntimeException e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}

	public MemorySegment segment() {
		return this.view;
	}

	@Override
	public void close() {
		try {
			int ignored = (int) UNMAP_VIEW_OF_FILE.invokeExact(this.view);
			ignored = (int) CLOSE_HANDLE.invokeExact(this.handle);
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}
}
