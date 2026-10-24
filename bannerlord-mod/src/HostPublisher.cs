using System;
using System.Collections.Generic;
using System.IO.MemoryMappedFiles;
using System.Threading;
using TaleWorlds.Engine;
using TaleWorlds.MountAndBlade;
using TaleWorlds.Library;

namespace MinecraftPassthrough
{
	internal sealed class HostPublisher : IDisposable
	{
		public const int FlagMission = 1;
		public const int FlagPlayer = 2;
		public const int FlagMounted = 8;
		public const int FlagBannerlordUi = 16;
		public const int FlagMenu = 32;
		public const int FlagTurkish = 64;
		public const int FlagPaused = 128;

		public const int GroundSize = 49;
		public const int MaxAgents = 128;
		public const int HurtCapacity = 16;

		private const string Name = "Local\\BLMC_Host";
		private const int Size = 16384;
		private const uint Magic = 0x434D4C42;
		private const uint Version = 3;
		private const long TeleportCountOffset = 188;
		private const long TeleportOffset = 192;
		private const long GroundOffset = 256;
		private const long AgentCountOffset = 10240;
		private const long AgentsOffset = 10256;
		private const int AgentSize = 32;
		private const long HurtCountOffset = 14400;
		private const long HurtOffset = 14416;
		private const int HurtSize = 16;
		private const long InputOffset = 14848;
		private const long LootCountOffset = 15104;
		private const long LootOffset = 15120;
		private const int LootSize = 16;
		public const int LootCapacity = 32;

		private readonly MemoryMappedFile _file;
		private readonly MemoryMappedViewAccessor _view;
		private uint _seq;
		private ulong _frame;
		private readonly float[] _ground = new float[GroundSize * GroundSize];
		private int _groundOriginX;
		private int _groundOriginY;
		private uint _groundVersion;
		private bool _groundDirty;
		private uint _hurtCount;
		private readonly Queue<(float damage, int attacker, Vec2 from)> _hurtQueue = new Queue<(float, int, Vec2)>();
		private uint _lootCount;
		private readonly Queue<LootDrop> _lootQueue = new Queue<LootDrop>();
		private uint _teleportCount;
		private Vec3 _teleportTarget;

		public HostPublisher()
		{
			_file = MemoryMappedFile.CreateOrOpen(Name, Size);
			_view = _file.CreateViewAccessor(0, Size);
			_view.Write(0, Magic);
			_view.Write(4, Version);
			_hurtCount = _view.ReadUInt32(HurtCountOffset);
			_lootCount = _view.ReadUInt32(LootCountOffset);
			_teleportCount = _view.ReadUInt32(TeleportCountOffset);
		}

		public void Publish(in HostFrame f, IReadOnlyList<AgentEntry> agents, in InputFrame input)
		{
			_seq |= 1;
			_view.Write(8, _seq);
			Thread.MemoryBarrier();

			_view.Write(12, f.Flags | (BannerlordConfig.Language == "Türkçe" ? FlagTurkish : 0));
			_view.Write(16, ++_frame);
			_view.Write(24, f.Time);
			WriteVec3d(32, f.CameraPosition);
			WriteVec3f(56, f.CameraRight);
			WriteVec3f(68, f.CameraUp);
			WriteVec3f(80, f.CameraForward);
			_view.Write(92, f.VerticalFov);
			_view.Write(96, f.AspectRatio);
			_view.Write(100, f.Near);
			_view.Write(104, f.Far);
			_view.Write(108, f.ScreenWidth);
			_view.Write(112, f.ScreenHeight);
			_view.Write(116, f.MissionId);
			WriteVec3d(120, f.PlayerPosition);
			WriteVec3f(144, f.BodyForward);
			WriteVec3f(156, f.LookDirection);
			WriteVec3f(168, f.Velocity);
			_view.Write(180, f.Health);
			_view.Write(184, f.HealthMax);
			WriteVec3d(TeleportOffset, _teleportTarget);
			_view.Write(TeleportCountOffset, _teleportCount);

			if (_groundDirty)
			{
				_groundDirty = false;
				_view.Write(220, _groundVersion);
				_view.Write(224, _groundOriginX);
				_view.Write(228, _groundOriginY);
				_view.Write(232, (uint)GroundSize);
				_view.WriteArray(GroundOffset, _ground, 0, _ground.Length);
			}

			int count = agents == null ? 0 : Math.Min(agents.Count, MaxAgents);
			_view.Write(AgentCountOffset, (uint)count);
			for (int i = 0; i < count; i++)
			{
				AgentEntry a = agents[i];
				long o = AgentsOffset + (long)i * AgentSize;
				_view.Write(o, (uint)a.Index);
				_view.Write(o + 4, a.Flags);
				_view.Write(o + 8, a.Position.x);
				_view.Write(o + 12, a.Position.y);
				_view.Write(o + 16, a.Position.z);
				_view.Write(o + 20, a.Facing.x);
				_view.Write(o + 24, a.Facing.y);
				_view.Write(o + 28, a.Height);
			}

			while (_hurtQueue.Count > 0)
			{
				(float damage, int attacker, Vec2 from) = _hurtQueue.Dequeue();
				long o = HurtOffset + (long)(_hurtCount % HurtCapacity) * HurtSize;
				_view.Write(o, damage);
				_view.Write(o + 4, attacker);
				_view.Write(o + 8, from.x);
				_view.Write(o + 12, from.y);
				_hurtCount++;
			}
			_view.Write(HurtCountOffset, _hurtCount);

			while (_lootQueue.Count > 0)
			{
				LootDrop drop = _lootQueue.Dequeue();
				long o = LootOffset + (long)(_lootCount % LootCapacity) * LootSize;
				_view.Write(o, drop.Position.x);
				_view.Write(o + 4, drop.Position.y);
				_view.Write(o + 8, drop.Position.z);
				_view.Write(o + 12, drop.Tier);
				_view.Write(o + 13, drop.Weapon);
				_view.Write(o + 14, drop.Flags);
				_lootCount++;
			}
			_view.Write(LootCountOffset, _lootCount);

			_view.Write(InputOffset, input.Flags);
			_view.Write(InputOffset + 4, input.KeysDown);
			for (int i = 0; i < InputFrame.KeyCount; i++)
			{
				_view.Write(InputOffset + 8 + i * 4, input.Presses == null ? 0u : input.Presses[i]);
			}
			_view.Write(InputOffset + 104, input.MouseX);
			_view.Write(InputOffset + 112, input.MouseY);
			_view.Write(InputOffset + 120, input.Scroll);
			_view.Write(InputOffset + 128, input.CursorX);
			_view.Write(InputOffset + 132, input.CursorY);
			_view.Write(InputOffset + 136, input.TypedCount);
			for (int i = 0; i < InputFrame.TypedCapacity; i++)
			{
				_view.Write(InputOffset + 140 + i * 4, input.Typed == null ? 0u : input.Typed[i]);
			}

			Thread.MemoryBarrier();
			_seq++;
			_view.Write(8, _seq);
		}

		public void SetGround(int originX, int originY, float[] heights)
		{
			Array.Copy(heights, _ground, _ground.Length);
			_groundOriginX = originX;
			_groundOriginY = originY;
			_groundVersion++;
			_groundDirty = true;
		}

		public void Teleport(Vec3 target)
		{
			_teleportTarget = target;
			_teleportCount++;
		}

		public void Hurt(float damage, int attackerIndex, Vec2 from)
		{
			_hurtQueue.Enqueue((damage, attackerIndex, from));
		}

		public void Loot(in LootDrop drop)
		{
			_lootQueue.Enqueue(drop);
		}

		public void PublishMenu(uint missionId, in InputFrame input)
		{
			float aspect = Screen.RealScreenResolutionHeight > 0 ? Screen.RealScreenResolutionWidth / Screen.RealScreenResolutionHeight : 16f / 9f;
			var frame = new HostFrame
			{
				Flags = FlagMission | FlagPlayer | FlagMenu,
				CameraRight = new Vec3(1f, 0f, 0f),
				CameraUp = new Vec3(0f, 0f, 1f),
				CameraForward = new Vec3(0f, 1f, 0f),
				VerticalFov = 1f,
				AspectRatio = aspect,
				Near = 0.1f,
				Far = 1000f,
				ScreenWidth = (uint)Screen.RealScreenResolutionWidth,
				ScreenHeight = (uint)Screen.RealScreenResolutionHeight,
				MissionId = missionId,
				BodyForward = new Vec3(0f, 1f, 0f),
				LookDirection = new Vec3(0f, 1f, 0f),
				Health = 100f,
				HealthMax = 100f,
			};
			Publish(frame, null, input);
		}

		public void PublishIdle()
		{
			Publish(new HostFrame(), null, new InputFrame());
		}

		public void Dispose()
		{
			_view.Dispose();
			_file.Dispose();
		}

		private void WriteVec3d(long offset, Vec3 v)
		{
			_view.Write(offset, (double)v.x);
			_view.Write(offset + 8, (double)v.y);
			_view.Write(offset + 16, (double)v.z);
		}

		private void WriteVec3f(long offset, Vec3 v)
		{
			_view.Write(offset, v.x);
			_view.Write(offset + 4, v.y);
			_view.Write(offset + 8, v.z);
		}
	}

	internal struct HostFrame
	{
		public int Flags;
		public double Time;
		public Vec3 CameraPosition;
		public Vec3 CameraRight;
		public Vec3 CameraUp;
		public Vec3 CameraForward;
		public float VerticalFov;
		public float AspectRatio;
		public float Near;
		public float Far;
		public uint ScreenWidth;
		public uint ScreenHeight;
		public uint MissionId;
		public Vec3 PlayerPosition;
		public Vec3 BodyForward;
		public Vec3 LookDirection;
		public Vec3 Velocity;
		public float Health;
		public float HealthMax;
	}

	internal struct AgentEntry
	{
		public const uint FlagEnemy = 1;
		public const uint FlagRider = 4;
		public const uint FlagMount = 8;

		public int Index;
		public uint Flags;
		public Vec3 Position;
		public Vec2 Facing;
		public float Height;
	}
}
