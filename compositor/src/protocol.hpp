#pragma once

#include <cstdint>

namespace protocol
{
	inline constexpr wchar_t host_name[] = L"Local\\BLMC_Host";
	inline constexpr uint32_t host_size = 16384;
	inline constexpr uint32_t host_magic = 0x434D4C42;
	inline constexpr uint32_t host_version = 3;
	inline constexpr uint32_t flag_mission = 1;

#pragma pack(push, 1)
	struct host_block
	{
		uint32_t magic;
		uint32_t version;
		uint32_t seq;
		uint32_t flags;
		uint64_t host_frame;
		double time;
		double cam_pos[3];
		float cam_right[3];
		float cam_up[3];
		float cam_fwd[3];
		float vfov;
		float aspect;
		float znear;
		float zfar;
		uint32_t screen_width;
		uint32_t screen_height;
		uint32_t mission_id;
		double player_pos[3];
		float body_fwd[3];
		float look_dir[3];
		float velocity[3];
		float health;
		float health_max;
		uint32_t swing_count;
		uint32_t hurt_count;
		int32_t main_item;
		int32_t off_item;
		uint32_t reserved;
	};
	static_assert(sizeof(host_block) == 208);

	inline constexpr wchar_t frame_name[] = L"Local\\BLMC_Frame";
	inline constexpr uint32_t frame_magic = 0x464D4C42;
	inline constexpr uint32_t slot_count = 3;
	inline constexpr uint32_t max_width = 1920;
	inline constexpr uint32_t max_height = 1200;
	inline constexpr uint64_t slots_offset = 4096;
	inline constexpr uint64_t slot_header = 256;
	inline constexpr uint64_t slot_stride = (slot_header + uint64_t(max_width) * max_height * 12 + 4095) / 4096 * 4096;
	inline constexpr uint64_t frame_size = slots_offset + slot_stride * slot_count;
	inline constexpr uint32_t slot_rows_bottom_up = 1;
	inline constexpr uint32_t slot_z_zero_to_one = 2;
	inline constexpr uint32_t slot_has_overlay = 4;

	struct frame_header
	{
		uint32_t magic;
		uint32_t version;
		int32_t latest_slot;
		uint32_t slot_count;
		uint64_t slot_stride;
		uint32_t max_width;
		uint32_t max_height;
		uint64_t frames_written;
	};

	struct slot_header_t
	{
		uint32_t seq;
		uint32_t width;
		uint32_t height;
		uint32_t flags;
		uint64_t host_frame;
		double cam_pos[3];
		float cam_right[3];
		float cam_up[3];
		float cam_fwd[3];
		float tan_half_fov_y;
		float aspect;
		float m22;
		float m32;
		float empty_depth;
		uint32_t color_offset;
		uint32_t depth_offset;
		uint32_t overlay_offset;
	};
	static_assert(sizeof(slot_header_t) == 116);
#pragma pack(pop)
}
