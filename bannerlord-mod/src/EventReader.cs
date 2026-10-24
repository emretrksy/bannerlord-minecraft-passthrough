using System;
using System.IO.MemoryMappedFiles;
using System.Threading;
using TaleWorlds.Library;

namespace MinecraftPassthrough
{
	internal sealed class EventReader : IDisposable
	{
		public const uint Explosion = 1;
		public const uint AgentHit = 4;
		public const uint PlayerDied = 5;

		private const string Name = "Local\\BLMC_Events";
		private const int Size = 16384;
		private const uint Magic = 0x454D4C42;
		private const long EventsOffset = 64;
		private const int EventSize = 32;
		private const int Capacity = 256;

		private readonly MemoryMappedFile _file;
		private readonly MemoryMappedViewAccessor _view;
		private ulong _read;

		public EventReader()
		{
			_file = MemoryMappedFile.CreateOrOpen(Name, Size);
			_view = _file.CreateViewAccessor(0, Size);
			_read = _view.ReadUInt32(0) == Magic ? _view.ReadUInt64(8) : 0;
		}

		public void Poll(Action<MinecraftEvent> handle)
		{
			if (_view.ReadUInt32(0) != Magic)
			{
				return;
			}

			ulong written = _view.ReadUInt64(8);
			Thread.MemoryBarrier();
			if (written - _read > Capacity)
			{
				_read = written - Capacity;
			}

			for (; _read < written; _read++)
			{
				long offset = EventsOffset + (long)(_read % Capacity) * EventSize;
				handle(new MinecraftEvent
				{
					Type = _view.ReadUInt32(offset),
					Arg = _view.ReadInt32(offset + 4),
					Position = new Vec3(_view.ReadSingle(offset + 8), _view.ReadSingle(offset + 12), _view.ReadSingle(offset + 16)),
					Radius = _view.ReadSingle(offset + 20),
					Power = _view.ReadSingle(offset + 24),
				});
			}
		}

		public void Dispose()
		{
			_view.Dispose();
			_file.Dispose();
		}
	}

	internal struct MinecraftEvent
	{
		public uint Type;
		public int Arg;
		public Vec3 Position;
		public float Radius;
		public float Power;
	}
}
