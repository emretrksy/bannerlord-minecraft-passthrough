#include "shared_memory.hpp"

#include <Windows.h>

shared_memory::~shared_memory()
{
	close();
}

bool shared_memory::open(const wchar_t *name, uint64_t size)
{
	close();
	_handle = CreateFileMappingW(INVALID_HANDLE_VALUE, nullptr, PAGE_READWRITE, static_cast<DWORD>(size >> 32), static_cast<DWORD>(size), name);
	if (_handle == nullptr)
		return false;

	_view = static_cast<uint8_t *>(MapViewOfFile(_handle, FILE_MAP_ALL_ACCESS, 0, 0, static_cast<SIZE_T>(size)));
	if (_view == nullptr)
	{
		close();
		return false;
	}
	return true;
}

void shared_memory::close()
{
	if (_view != nullptr)
		UnmapViewOfFile(_view);
	if (_handle != nullptr)
		CloseHandle(_handle);
	_view = nullptr;
	_handle = nullptr;
}
