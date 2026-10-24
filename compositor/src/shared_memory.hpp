#pragma once

#include <cstdint>

class shared_memory
{
public:
	shared_memory() = default;
	~shared_memory();
	shared_memory(const shared_memory &) = delete;
	shared_memory &operator=(const shared_memory &) = delete;

	bool open(const wchar_t *name, uint64_t size);
	void close();
	uint8_t *data() const { return _view; }

private:
	void *_handle = nullptr;
	uint8_t *_view = nullptr;
};
