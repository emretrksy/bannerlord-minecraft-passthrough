using System;
using System.IO.MemoryMappedFiles;
using System.Threading;
using TaleWorlds.Library;

namespace MinecraftPassthrough
{
	internal sealed class PlayerLink : IDisposable
	{
		public const uint FlagValid = 1;
		public const uint FlagFirstPerson = 2;
		public const uint FlagDead = 4;
		public const uint FlagCursor = 8;
		public const uint FlagFocus = 16;
		public const uint FlagMounted = 32;
		public const uint FlagPaused = 64;

		private const string Name = "Local\\BLMC_Player";
		private const int Size = 4096;
		private const uint Magic = 0x504D4C42;
		private const int Length = 160;
		private const float StaleSeconds = 0.5f;

		private readonly MemoryMappedFile _file;
		private readonly MemoryMappedViewAccessor _view;
		private readonly byte[] _copy = new byte[Length];
		private ulong _lastFrame;
		private float _sinceChange = float.MaxValue;

		public PlayerLink()
		{
			_file = MemoryMappedFile.CreateOrOpen(Name, Size);
			_view = _file.CreateViewAccessor(0, Size);
		}

		public PlayerState Poll(float dt, uint missionId)
		{
			for (int attempt = 0; attempt < 4; attempt++)
			{
				uint before = _view.ReadUInt32(8);
				Thread.MemoryBarrier();
				if ((before & 1) != 0)
				{
					continue;
				}

				_view.ReadArray(0, _copy, 0, Length);
				Thread.MemoryBarrier();
				if (_view.ReadUInt32(8) != before)
				{
					continue;
				}

				if (BitConverter.ToUInt32(_copy, 0) != Magic)
				{
					return null;
				}

				ulong frame = BitConverter.ToUInt64(_copy, 16);
				_sinceChange = frame != _lastFrame ? 0f : _sinceChange + dt;
				_lastFrame = frame;
				var state = new PlayerState(_copy);
				return _sinceChange < StaleSeconds && state.Has(FlagValid) && state.MissionId == missionId ? state : null;
			}
			return null;
		}

		public void Dispose()
		{
			_view.Dispose();
			_file.Dispose();
		}
	}

	internal sealed class PlayerState
	{
		public readonly uint Flags;
		public readonly Vec3 CameraPosition;
		public readonly Vec3 CameraRight;
		public readonly Vec3 CameraUp;
		public readonly Vec3 CameraForward;
		public readonly float VerticalFov;
		public readonly uint MissionId;
		public readonly Vec3 Feet;
		public readonly Vec3 BodyForward;
		public readonly Vec3 Focus;

		public PlayerState(byte[] b)
		{
			Flags = BitConverter.ToUInt32(b, 12);
			CameraPosition = Vec3d(b, 24);
			CameraRight = Vec3f(b, 48);
			CameraUp = Vec3f(b, 60);
			CameraForward = Vec3f(b, 72);
			VerticalFov = BitConverter.ToSingle(b, 84);
			MissionId = BitConverter.ToUInt32(b, 88);
			Feet = Vec3d(b, 96);
			BodyForward = Vec3f(b, 120);
			Focus = Vec3d(b, 136);
		}

		public bool Has(uint flag) => (Flags & flag) != 0;

		public MatrixFrame CameraFrame => new MatrixFrame(new Mat3(CameraRight, CameraUp, -CameraForward), CameraPosition);

		private static Vec3 Vec3d(byte[] b, int o) =>
			new Vec3((float)BitConverter.ToDouble(b, o), (float)BitConverter.ToDouble(b, o + 8), (float)BitConverter.ToDouble(b, o + 16));

		private static Vec3 Vec3f(byte[] b, int o) =>
			new Vec3(BitConverter.ToSingle(b, o), BitConverter.ToSingle(b, o + 4), BitConverter.ToSingle(b, o + 8));
	}
}
