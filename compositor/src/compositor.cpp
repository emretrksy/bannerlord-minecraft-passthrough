
#include "protocol.hpp"
#include "shared_memory.hpp"

#include <Windows.h>
#include <reshade.hpp>

#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstring>

using namespace reshade::api;

extern "C" __declspec(dllexport) const char *NAME = "Minecraft Passthrough";
extern "C" __declspec(dllexport) const char *DESCRIPTION = "Composites Minecraft's picture (colour + depth) into Bannerlord.";

namespace
{
	constexpr char effect_name[] = "MCPassthrough.fx";
	constexpr double stale_seconds = 0.5;
	constexpr size_t pose_history = 16;

	struct frame_textures
	{
		uint32_t width = 0;
		uint32_t height = 0;
		resource color = {};
		resource depth = {};
		resource overlay = {};
		resource_view color_srv = {};
		resource_view depth_srv = {};
		resource_view overlay_srv = {};
	};

	struct state
	{
		shared_memory host;
		shared_memory frame;
		bool host_open = false;
		bool frame_open = false;

		std::array<protocol::host_block, pose_history> poses = {};
		size_t pose_count = 0;
		uint64_t last_host_frame = 0;
		LARGE_INTEGER last_host_change = {};

		uint64_t uploaded_frames_written = 0;
		protocol::slot_header_t slot = {};
		bool have_slot = false;
		frame_textures textures;
	};

	state g;

	void report(const char *status)
	{
		static const char *last = nullptr;
		if (status != last)
		{
			last = status;
			reshade::log::message(reshade::log::level::info, status);
		}
	}

	double seconds_since(LARGE_INTEGER then)
	{
		LARGE_INTEGER now, freq;
		QueryPerformanceCounter(&now);
		QueryPerformanceFrequency(&freq);
		return double(now.QuadPart - then.QuadPart) / double(freq.QuadPart);
	}

	template <typename T>
	bool read_consistent(const uint8_t *src, const volatile uint32_t *seq, T &out)
	{
		for (int attempt = 0; attempt < 4; ++attempt)
		{
			const uint32_t before = *seq;
			std::atomic_thread_fence(std::memory_order_acquire);
			if (before & 1)
				continue;
			std::memcpy(&out, src, sizeof(T));
			std::atomic_thread_fence(std::memory_order_acquire);
			if (*seq == before)
				return true;
		}
		return false;
	}

	void poll_host()
	{
		if (!g.host_open)
			g.host_open = g.host.open(protocol::host_name, protocol::host_size);
		if (!g.host_open)
			return;

		const uint8_t *base = g.host.data();
		protocol::host_block block;
		if (!read_consistent(base, reinterpret_cast<const volatile uint32_t *>(base + offsetof(protocol::host_block, seq)), block))
			return;
		if (block.magic != protocol::host_magic || block.version != protocol::host_version)
			return;

		if (block.host_frame != g.last_host_frame)
		{
			g.last_host_frame = block.host_frame;
			QueryPerformanceCounter(&g.last_host_change);
			g.poses[g.pose_count % pose_history] = block;
			g.pose_count++;
		}
	}

	void destroy_textures(device *device)
	{
		frame_textures &t = g.textures;
		if (t.color_srv.handle != 0) device->destroy_resource_view(t.color_srv);
		if (t.depth_srv.handle != 0) device->destroy_resource_view(t.depth_srv);
		if (t.overlay_srv.handle != 0) device->destroy_resource_view(t.overlay_srv);
		if (t.overlay.handle != 0) device->destroy_resource(t.overlay);
		if (t.color.handle != 0) device->destroy_resource(t.color);
		if (t.depth.handle != 0) device->destroy_resource(t.depth);
		t = {};
	}

	bool create_textures(effect_runtime *runtime, uint32_t width, uint32_t height)
	{
		device *const device = runtime->get_device();
		destroy_textures(device);

		const auto usage = resource_usage::shader_resource | resource_usage::copy_dest;
		frame_textures &t = g.textures;
		if (!device->create_resource(resource_desc(width, height, 1, 1, format::r8g8b8a8_unorm, 1, memory_heap::default_, usage), nullptr, resource_usage::shader_resource, &t.color) ||
			!device->create_resource(resource_desc(width, height, 1, 1, format::r32_float, 1, memory_heap::default_, usage), nullptr, resource_usage::shader_resource, &t.depth) ||
			!device->create_resource(resource_desc(width, height, 1, 1, format::r8g8b8a8_unorm, 1, memory_heap::default_, usage), nullptr, resource_usage::shader_resource, &t.overlay) ||
			!device->create_resource_view(t.color, resource_usage::shader_resource, resource_view_desc(format::r8g8b8a8_unorm), &t.color_srv) ||
			!device->create_resource_view(t.depth, resource_usage::shader_resource, resource_view_desc(format::r32_float), &t.depth_srv) ||
			!device->create_resource_view(t.overlay, resource_usage::shader_resource, resource_view_desc(format::r8g8b8a8_unorm), &t.overlay_srv))
		{
			destroy_textures(device);
			return false;
		}

		t.width = width;
		t.height = height;
		runtime->update_texture_bindings("MCPT_COLOR", t.color_srv, t.color_srv);
		runtime->update_texture_bindings("MCPT_DEPTH", t.depth_srv, t.depth_srv);
		runtime->update_texture_bindings("MCPT_OVERLAY", t.overlay_srv, t.overlay_srv);
		return true;
	}

	void poll_frame(effect_runtime *runtime)
	{
		if (!g.frame_open)
			g.frame_open = g.frame.open(protocol::frame_name, protocol::frame_size);
		if (!g.frame_open)
			return;

		const uint8_t *base = g.frame.data();
		const auto *header = reinterpret_cast<const volatile protocol::frame_header *>(base);
		if (header->magic != protocol::frame_magic)
			return;
		const uint64_t written = header->frames_written;
		const int32_t slot_index = header->latest_slot;
		if (written == g.uploaded_frames_written || slot_index < 0 || slot_index >= int32_t(protocol::slot_count))
			return;

		const uint8_t *slot = base + protocol::slots_offset + protocol::slot_stride * uint32_t(slot_index);
		const auto *seq = reinterpret_cast<const volatile uint32_t *>(slot);
		protocol::slot_header_t head;
		if (!read_consistent(slot, seq, head))
			return;
		if (head.width == 0 || head.height == 0 || head.width > protocol::max_width || head.height > protocol::max_height)
			return;
		const uint64_t image_size = uint64_t(head.width) * head.height * 4;
		const auto fits = [image_size](uint32_t offset) { return offset >= protocol::slot_header && offset + image_size <= protocol::slot_stride; };
		if (!fits(head.color_offset) || !fits(head.depth_offset) || ((head.flags & protocol::slot_has_overlay) && !fits(head.overlay_offset)))
			return;

		if ((head.width != g.textures.width || head.height != g.textures.height) && !create_textures(runtime, head.width, head.height))
		{
			report("MCPassthrough: could not create the Minecraft frame textures");
			return;
		}

		device *const device = runtime->get_device();
		device->update_texture_region({ const_cast<uint8_t *>(slot + head.color_offset), head.width * 4, head.width * head.height * 4 }, g.textures.color, 0);
		device->update_texture_region({ const_cast<uint8_t *>(slot + head.depth_offset), head.width * 4, head.width * head.height * 4 }, g.textures.depth, 0);
		if (head.flags & protocol::slot_has_overlay)
			device->update_texture_region({ const_cast<uint8_t *>(slot + head.overlay_offset), head.width * 4, head.width * head.height * 4 }, g.textures.overlay, 0);

		std::atomic_thread_fence(std::memory_order_acquire);
		if (*seq != head.seq)
			return;

		g.slot = head;
		g.have_slot = true;
		g.uploaded_frames_written = written;
	}

	void set_float(effect_runtime *runtime, const char *name, const float *values, size_t count)
	{
		const effect_uniform_variable variable = runtime->find_uniform_variable(effect_name, name);
		if (variable.handle != 0)
			runtime->set_uniform_value_float(variable, values, count);
	}

	void set_float(effect_runtime *runtime, const char *name, float x, float y = 0.0f, float z = 0.0f)
	{
		const float values[3] = { x, y, z };
		set_float(runtime, name, values, 3);
	}

	void on_begin_effects(effect_runtime *runtime, command_list *, resource_view, resource_view)
	{
		poll_host();
		poll_frame(runtime);

		const bool host_live = g.pose_count != 0 && seconds_since(g.last_host_change) < stale_seconds;
		const protocol::host_block *latest = host_live ? &g.poses[(g.pose_count - 1) % pose_history] : nullptr;
		const bool enabled = latest != nullptr && (latest->flags & protocol::flag_mission) != 0 && g.have_slot;
		set_float(runtime, "uEnabled", enabled ? 1.0f : 0.0f);
		if (!enabled)
		{
			report(!g.host_open ? "MCPassthrough: waiting for Bannerlord's shared memory"
				: latest == nullptr ? "MCPassthrough: Bannerlord is not publishing"
				: (latest->flags & protocol::flag_mission) == 0 ? "MCPassthrough: not in a mission"
				: "MCPassthrough: waiting for a Minecraft frame");
			return;
		}
		report("MCPassthrough: compositing");

		uint32_t lag = 0;
		reshade::get_config_value(runtime, "MCPassthrough", "PoseLag", lag);
		lag = std::min<uint32_t>(lag, uint32_t(std::min(g.pose_count, pose_history) - 1));
		const protocol::host_block &bl = g.poses[(g.pose_count - 1 - lag) % pose_history];
		const protocol::slot_header_t &mc = g.slot;

		const float bl_tan_y = std::tan(bl.vfov * 0.5f);
		set_float(runtime, "uBlRight", bl.cam_right, 3);
		set_float(runtime, "uBlUp", bl.cam_up, 3);
		set_float(runtime, "uBlFwd", bl.cam_fwd, 3);
		set_float(runtime, "uBlTan", bl_tan_y * bl.aspect, bl_tan_y);
		set_float(runtime, "uBlNearFar", bl.znear, bl.zfar);

		set_float(runtime, "uMcOffset",
			float(mc.cam_pos[0] - bl.cam_pos[0]), float(mc.cam_pos[1] - bl.cam_pos[1]), float(mc.cam_pos[2] - bl.cam_pos[2]));
		set_float(runtime, "uMcRight", mc.cam_right, 3);
		set_float(runtime, "uMcUp", mc.cam_up, 3);
		set_float(runtime, "uMcFwd", mc.cam_fwd, 3);
		set_float(runtime, "uMcTan", mc.tan_half_fov_y * mc.aspect, mc.tan_half_fov_y);
		set_float(runtime, "uMcDepth", mc.m22, mc.m32, mc.empty_depth);
		set_float(runtime, "uMcFlags",
			(mc.flags & protocol::slot_rows_bottom_up) ? 1.0f : 0.0f,
			(mc.flags & protocol::slot_z_zero_to_one) ? 1.0f : 0.0f);
		set_float(runtime, "uMcTexel", 1.0f / float(mc.width), 1.0f / float(mc.height));
		set_float(runtime, "uHasOverlay", (mc.flags & protocol::slot_has_overlay) ? 1.0f : 0.0f);
	}

	void on_reloaded_effects(effect_runtime *runtime)
	{
		if (g.textures.width != 0)
		{
			runtime->update_texture_bindings("MCPT_COLOR", g.textures.color_srv, g.textures.color_srv);
			runtime->update_texture_bindings("MCPT_DEPTH", g.textures.depth_srv, g.textures.depth_srv);
			runtime->update_texture_bindings("MCPT_OVERLAY", g.textures.overlay_srv, g.textures.overlay_srv);
		}
	}

	void on_destroy_effect_runtime(effect_runtime *runtime)
	{
		destroy_textures(runtime->get_device());
		g.have_slot = false;
		g.uploaded_frames_written = 0;
	}
}

BOOL APIENTRY DllMain(HMODULE module, DWORD reason, LPVOID)
{
	switch (reason)
	{
	case DLL_PROCESS_ATTACH:
		if (!reshade::register_addon(module))
			return FALSE;
		reshade::register_event<reshade::addon_event::reshade_begin_effects>(on_begin_effects);
		reshade::register_event<reshade::addon_event::reshade_reloaded_effects>(on_reloaded_effects);
		reshade::register_event<reshade::addon_event::destroy_effect_runtime>(on_destroy_effect_runtime);
		break;
	case DLL_PROCESS_DETACH:
		reshade::unregister_addon(module);
		break;
	}
	return TRUE;
}
