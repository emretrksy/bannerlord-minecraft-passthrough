using System;
using System.IO.MemoryMappedFiles;
using System.Text;
using System.Threading;

namespace MinecraftPassthrough
{
	internal sealed class InventoryLink : IDisposable
	{
		public const uint ModeFullKit = 1;
		public const uint ModeCampaign = 2;
		public const uint ModeTournament = 3;

		private const string Name = "Local\\BLMC_Inventory";
		private const int Size = 131072;
		private const uint Magic = 0x494D4C42;
		private const long HostOffset = 64;
		private const long HostData = 128;
		private const long SteveOffset = 65536;
		private const long SteveData = SteveOffset + 64;
		public const int MaxLength = 65536 - 128;

		private readonly MemoryMappedFile _file;
		private readonly MemoryMappedViewAccessor _view;
		private uint _hostSeq;
		private uint _seenSteveSeq;

		public InventoryLink()
		{
			_file = MemoryMappedFile.CreateOrOpen(Name, Size);
			_view = _file.CreateViewAccessor(0, Size);
			_view.Write(0, Magic);
			_view.Write(4, 1u);
			_seenSteveSeq = _view.ReadUInt32(SteveOffset);
		}

		public void Offer(uint missionId, uint mode, string inventory, string grants, float health = 1f)
		{
			byte[] text = Encoding.UTF8.GetBytes(inventory ?? "");
			byte[] goods = Encoding.UTF8.GetBytes(grants ?? "");
			if (text.Length + goods.Length > MaxLength)
			{
				text = new byte[0];
				goods = new byte[0];
			}

			_hostSeq |= 1;
			_view.Write(HostOffset, _hostSeq);
			Thread.MemoryBarrier();
			_view.Write(HostOffset + 4, missionId);
			_view.Write(HostOffset + 8, mode);
			_view.Write(HostOffset + 12, text.Length);
			_view.Write(HostOffset + 16, goods.Length);
			_view.Write(HostOffset + 20, health);
			_view.WriteArray(HostData, text, 0, text.Length);
			_view.WriteArray(HostData + text.Length, goods, 0, goods.Length);
			Thread.MemoryBarrier();
			_hostSeq++;
			_view.Write(HostOffset, _hostSeq);
		}

		public InventoryReport Poll(uint missionId)
		{
			for (int attempt = 0; attempt < 4; attempt++)
			{
				uint before = _view.ReadUInt32(SteveOffset);
				Thread.MemoryBarrier();
				if ((before & 1) != 0 || before == _seenSteveSeq)
				{
					return null;
				}

				uint mission = _view.ReadUInt32(SteveOffset + 4);
				int length = _view.ReadInt32(SteveOffset + 8);
				float health = _view.ReadSingle(SteveOffset + 12);
				if (length < 0 || length > MaxLength)
				{
					return null;
				}
				var text = new byte[length];
				_view.ReadArray(SteveData, text, 0, length);
				Thread.MemoryBarrier();
				if (_view.ReadUInt32(SteveOffset) != before)
				{
					continue;
				}

				_seenSteveSeq = before;
				return mission == missionId ? new InventoryReport(Encoding.UTF8.GetString(text), health) : null;
			}
			return null;
		}

		public void Dispose()
		{
			_view.Dispose();
			_file.Dispose();
		}
	}

	internal sealed class InventoryReport
	{
		public readonly string Inventory;
		public readonly float Health;

		public InventoryReport(string inventory, float health)
		{
			Inventory = inventory;
			Health = health;
		}
	}
}
