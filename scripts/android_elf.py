import struct


def verify_elf(data, abi):
    machines = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40), "x86_64": (2, 62), "x86": (1, 3)}
    elf_class, machine = machines[abi]
    if len(data) < 64 or data[:6] != b"\x7fELF" + bytes((elf_class, 1)) or struct.unpack_from("<H", data, 18)[0] != machine:
        raise ValueError(f"Wrong ELF architecture for {abi}")
    if elf_class == 2:
        start = struct.unpack_from("<Q", data, 32)[0]
        size, count = struct.unpack_from("<HH", data, 54)
        align_at, fmt = 48, "<Q"
    else:
        start = struct.unpack_from("<I", data, 28)[0]
        size, count = struct.unpack_from("<HH", data, 42)
        align_at, fmt = 28, "<I"
    if size < align_at + struct.calcsize(fmt) or start + size * count > len(data):
        raise ValueError(f"Invalid ELF program headers for {abi}")
    loads = [struct.unpack_from(fmt, data, offset + align_at)[0]
             for offset in range(start, start + size * count, size)
             if struct.unpack_from("<I", data, offset)[0] == 1]
    if not loads or min(loads) < (16384 if elf_class == 2 else 4096):
        raise ValueError(f"Invalid ELF load alignment for {abi}")
