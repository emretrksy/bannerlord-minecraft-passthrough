using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using TaleWorlds.InputSystem;

namespace MinecraftPassthrough
{
	internal struct InputFrame
	{
		public const uint FlagControlling = 1;
		public const uint FlagCursor = 2;
		public const int KeyCount = 24;

		public const int Forward = 0, Back = 1, Left = 2, Right = 3, Jump = 4, Sneak = 5, Sprint = 6;
		public const int Attack = 7, Use = 8, PickItem = 9, Drop = 10, Inventory = 11, SwapOffhand = 12;
		public const int Perspective = 13, Hotbar1 = 14;
		public const int DevPanel = 23;
		public const int TypedCapacity = 16;
		public const uint TypedBackspace = 8;
		public const uint TypedEnter = 13;
		public const uint TypedEscape = 27;

		public uint Flags;
		public uint KeysDown;
		public uint[] Presses;
		public double MouseX;
		public double MouseY;
		public double Scroll;
		public float CursorX;
		public float CursorY;
		public uint TypedCount;
		public uint[] Typed;
	}

	internal sealed class InputCapture
	{
		private static readonly InputKey[] Keys =
		{
			InputKey.W, InputKey.S, InputKey.A, InputKey.D, InputKey.Space, InputKey.LeftShift, InputKey.LeftControl,
			InputKey.LeftMouseButton, InputKey.RightMouseButton, InputKey.MiddleMouseButton, InputKey.Q, InputKey.E,
			InputKey.F, InputKey.F5,
			InputKey.D1, InputKey.D2, InputKey.D3, InputKey.D4, InputKey.D5, InputKey.D6, InputKey.D7, InputKey.D8, InputKey.D9,
			InputKey.F10,
		};

		private readonly uint[] _presses = new uint[InputFrame.KeyCount];
		private double _mouseX;
		private double _mouseY;
		private double _scroll;
		private uint _scriptedDown;
		private uint _windowsDown;
		private readonly uint[] _typed = new uint[InputFrame.TypedCapacity];
		private uint _typedCount;
		private readonly bool[] _textDown = new bool[256];
		private bool _textPrimed;
		private readonly byte[] _keyboard = new byte[256];
		private readonly StringBuilder _chars = new StringBuilder(8);

		private const int VkLeftButton = 0x01;
		private const int VkRightButton = 0x02;

		[DllImport("user32.dll")]
		private static extern short GetAsyncKeyState(int key);

		[DllImport("user32.dll")]
		private static extern IntPtr GetForegroundWindow();

		[DllImport("user32.dll")]
		private static extern short GetKeyState(int key);

		[DllImport("user32.dll")]
		private static extern IntPtr GetKeyboardLayout(uint threadId);

		[DllImport("user32.dll")]
		private static extern uint MapVirtualKeyEx(uint code, uint mapType, IntPtr layout);

		[DllImport("user32.dll", CharSet = CharSet.Unicode)]
		private static extern int ToUnicodeEx(uint virtualKey, uint scanCode, byte[] keyState, StringBuilder buffer, int bufferSize, uint flags, IntPtr layout);

		[DllImport("user32.dll")]
		private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);

		private static bool IsForeground()
		{
			GetWindowThreadProcessId(GetForegroundWindow(), out uint processId);
			return processId == (uint)Process.GetCurrentProcess().Id;
		}

		private uint SetFromWindows(uint down, int key, int virtualKey)
		{
			uint bit = 1u << key;
			bool isDown = (GetAsyncKeyState(virtualKey) & 0x8000) != 0;
			if (isDown && (_windowsDown & bit) == 0)
			{
				_presses[key]++;
			}
			_windowsDown = isDown ? _windowsDown | bit : _windowsDown & ~bit;
			return isDown ? down | bit : down;
		}

		public void PressCloseScreen()
		{
			Type(InputFrame.TypedEscape);
		}

		private void Type(uint codepoint)
		{
			_typed[_typedCount % InputFrame.TypedCapacity] = codepoint;
			_typedCount++;
		}

		private static bool IsTextKey(int vk)
		{
			return vk == 0x08 || vk == 0x0D || vk == 0x20 || (vk >= 0x30 && vk <= 0x39) || (vk >= 0x41 && vk <= 0x5A)
				|| (vk >= 0x60 && vk <= 0x6F) || (vk >= 0xBA && vk <= 0xC0) || (vk >= 0xDB && vk <= 0xDF) || vk == 0xE2;
		}

		private void CaptureText()
		{
			IntPtr layout = GetKeyboardLayout(GetWindowThreadProcessId(GetForegroundWindow(), out uint _));
			for (int vk = 0; vk < 256; vk++)
			{
				_keyboard[vk] = (GetAsyncKeyState(vk) & 0x8000) != 0 ? (byte)0x80 : (byte)0;
			}
			_keyboard[0x14] |= (byte)(GetKeyState(0x14) & 1);
			bool shortcut = _keyboard[0x11] != 0 && _keyboard[0x12] == 0;

			for (int vk = 0; vk < 256; vk++)
			{
				if (!IsTextKey(vk))
				{
					continue;
				}
				bool down = _keyboard[vk] != 0;
				bool pressed = down && !_textDown[vk] && _textPrimed;
				_textDown[vk] = down;
				if (!pressed || shortcut)
				{
					continue;
				}
				if (vk == 0x08)
				{
					Type(InputFrame.TypedBackspace);
					continue;
				}
				if (vk == 0x0D)
				{
					Type(InputFrame.TypedEnter);
					continue;
				}

				_chars.Clear();
				int length = ToUnicodeEx((uint)vk, MapVirtualKeyEx((uint)vk, 0, layout), _keyboard, _chars, _chars.Capacity, 4, layout);
				for (int i = 0; i < length && i < _chars.Length; i++)
				{
					if (!char.IsControl(_chars[i]))
					{
						Type(_chars[i]);
					}
				}
			}
			_textPrimed = true;
		}

		public uint ScriptedDown
		{
			get => _scriptedDown;
			set
			{
				for (int i = 0; i < Keys.Length; i++)
				{
					uint bit = 1u << i;
					if ((value & bit) != 0 && (_scriptedDown & bit) == 0)
					{
						_presses[i]++;
					}
				}
				_scriptedDown = value;
			}
		}

		public void ScriptedLook(float dx, float dy)
		{
			_mouseX += dx;
			_mouseY += dy;
		}

		public InputFrame Capture(bool controlling, bool cursor)
		{
			uint down = 0;
			if (controlling)
			{
				for (int i = 0; i < Keys.Length; i++)
				{
					if (Input.IsKeyDown(Keys[i]))
					{
						down |= 1u << i;
					}
					if (Input.IsKeyPressed(Keys[i]))
					{
						_presses[i]++;
					}
				}

				if (cursor && IsForeground())
				{
					down = SetFromWindows(down, InputFrame.Attack, VkLeftButton);
					down = SetFromWindows(down, InputFrame.Use, VkRightButton);
					CaptureText();
				}
				else
				{
					_textPrimed = false;
				}

				_mouseX += Input.MouseMoveX;
				_mouseY += Input.MouseMoveY;
				_scroll += Input.DeltaMouseScroll;
			}
			else
			{
				_textPrimed = false;
			}

			return new InputFrame
			{
				Flags = (controlling ? InputFrame.FlagControlling : 0) | (cursor ? InputFrame.FlagCursor : 0),
				KeysDown = down | _scriptedDown,
				Presses = _presses,
				MouseX = _mouseX,
				MouseY = _mouseY,
				Scroll = _scroll,
				CursorX = Input.MousePositionRanged.x,
				CursorY = Input.MousePositionRanged.y,
				TypedCount = _typedCount,
				Typed = _typed,
			};
		}
	}
}
