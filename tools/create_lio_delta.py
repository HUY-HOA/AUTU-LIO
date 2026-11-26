#!/usr/bin/env python3
"""Create and self-verify a compact LIODELTA1 patch between two APK files.

The patch stores copy references to content-defined chunks in the old APK and
literal bytes for changed chunks. The operation stream is gzip-compressed.
"""
from __future__ import annotations

import argparse
import hashlib
import gzip
import mmap
import struct
import sys
from collections import defaultdict
from pathlib import Path

MAGIC = b"LIODELTA1"  # 9 bytes
WINDOW = 64
MIN_CHUNK = 1024
MAX_CHUNK = 16 * 1024
MASK = (1 << 13) - 1  # ~8 KiB average chunk size
COPY = 0
DATA = 1
END = 255


def _rotl64(value: int, count: int = 1) -> int:
    count &= 63
    return ((value << count) | (value >> (64 - count))) & 0xFFFFFFFFFFFFFFFF if count else value


_TABLE = tuple(
    int.from_bytes(hashlib.blake2b(bytes((i,)), digest_size=8, person=b"LIODLT1").digest(), "big")
    for i in range(256)
)
_REMOVE = tuple(_rotl64(value, WINDOW) for value in _TABLE)


def chunks(data: mmap.mmap):
    """Yield (offset, length) content-defined chunks using a 64-byte buzhash."""
    start = 0
    rolling = 0
    window = bytearray(WINDOW)
    count = 0
    size = len(data)
    for pos in range(size):
        value = data[pos]
        if count < WINDOW:
            window[count] = value
            count += 1
            rolling = _rotl64(rolling) ^ _TABLE[value]
        else:
            slot = (pos - start) % WINDOW
            outgoing = window[slot]
            window[slot] = value
            rolling = _rotl64(rolling) ^ _REMOVE[outgoing] ^ _TABLE[value]
        length = pos + 1 - start
        if length >= MIN_CHUNK and ((rolling & MASK) == 0 or length >= MAX_CHUNK):
            yield start, length
            start = pos + 1
            rolling = 0
            count = 0
            window = bytearray(WINDOW)
    if start < size:
        yield start, size - start


def digest(data: bytes | mmap.mmap, offset: int, length: int) -> bytes:
    return hashlib.blake2s(data[offset:offset + length], digest_size=16, person=b"LIODLT1").digest()


def build_patch(old_path: Path, new_path: Path, output_path: Path) -> tuple[int, int]:
    old_size = old_path.stat().st_size
    new_size = new_path.stat().st_size
    if old_size <= 0 or new_size <= 0:
        raise ValueError("input APKs must be non-empty")
    with old_path.open("rb") as old_file, new_path.open("rb") as new_file:
        with mmap.mmap(old_file.fileno(), 0, access=mmap.ACCESS_READ) as old_map, \
             mmap.mmap(new_file.fileno(), 0, access=mmap.ACCESS_READ) as new_map:
            index: dict[bytes, list[tuple[int, int]]] = defaultdict(list)
            for offset, length in chunks(old_map):
                index[digest(old_map, offset, length)].append((offset, length))

            operations = 0
            with output_path.open("wb") as raw_output:
                with gzip.GzipFile(filename="", mode="wb", fileobj=raw_output, compresslevel=9, mtime=0) as patch:
                    patch.write(MAGIC)
                    patch.write(struct.pack(">Q", new_size))
                    pending_copy: tuple[int, int] | None = None
                    pending_data = bytearray()

                    def flush_copy() -> None:
                        nonlocal pending_copy, operations
                        if pending_copy is None:
                            return
                        offset, length = pending_copy
                        patch.write(bytes((COPY,)) + struct.pack(">QI", offset, length))
                        operations += 1
                        pending_copy = None

                    def flush_data() -> None:
                        nonlocal pending_data, operations
                        if not pending_data:
                            return
                        patch.write(bytes((DATA,)) + struct.pack(">I", len(pending_data)))
                        patch.write(pending_data)
                        operations += 1
                        pending_data = bytearray()

                    for target_offset, target_length in chunks(new_map):
                        target_hash = digest(new_map, target_offset, target_length)
                        match = None
                        for old_offset, old_length in index.get(target_hash, ()):
                            if old_length == target_length and old_map[old_offset:old_offset + old_length] == new_map[target_offset:target_offset + target_length]:
                                match = old_offset
                                break
                        if match is not None:
                            flush_data()
                            if pending_copy and pending_copy[0] + pending_copy[1] == match:
                                pending_copy = (pending_copy[0], pending_copy[1] + target_length)
                            else:
                                flush_copy()
                                pending_copy = (match, target_length)
                        else:
                            flush_copy()
                            pending_data.extend(new_map[target_offset:target_offset + target_length])
                            if len(pending_data) >= 256 * 1024:
                                flush_data()
                    flush_copy()
                    flush_data()
                    patch.write(bytes((END,)))
                    operations += 1
        patch_size = output_path.stat().st_size
    if patch_size <= 0:
        raise RuntimeError("patch creation produced an empty file")
    return patch_size, operations


def apply_patch(old_path: Path, patch_path: Path, result_path: Path) -> None:
    with old_path.open("rb") as old_file, patch_path.open("rb") as patch_file, result_path.open("wb") as result:
        with mmap.mmap(old_file.fileno(), 0, access=mmap.ACCESS_READ) as old_map:
            with gzip.GzipFile(fileobj=patch_file, mode="rb") as patch:
                if patch.read(len(MAGIC)) != MAGIC:
                    raise ValueError("invalid LIO delta magic")
                header = patch.read(8)
                if len(header) != 8:
                    raise ValueError("truncated LIO delta header")
                target_size = struct.unpack(">Q", header)[0]
                written = 0
                operations = 0
                while True:
                    tag = patch.read(1)
                    if len(tag) != 1:
                        raise ValueError("missing delta terminator")
                    kind = tag[0]
                    if kind == END:
                        break
                    operations += 1
                    if operations > 2_000_000:
                        raise ValueError("too many delta operations")
                    if kind == COPY:
                        record = patch.read(12)
                        if len(record) != 12:
                            raise ValueError("truncated copy record")
                        offset, length = struct.unpack(">QI", record)
                        if length <= 0 or offset > len(old_map) or offset + length > len(old_map):
                            raise ValueError("copy record is outside source APK")
                        result.write(old_map[offset:offset + length])
                        written += length
                    elif kind == DATA:
                        record = patch.read(4)
                        if len(record) != 4:
                            raise ValueError("truncated data record")
                        length = struct.unpack(">I", record)[0]
                        if length <= 0 or length > 256 * 1024:
                            raise ValueError("invalid data record length")
                        literal = patch.read(length)
                        if len(literal) != length:
                            raise ValueError("truncated data record payload")
                        result.write(literal)
                        written += length
                    else:
                        raise ValueError(f"unknown delta operation {kind}")
                    if written > target_size:
                        raise ValueError("patch output exceeds target size")
                if written != target_size:
                    raise ValueError(f"reconstructed size mismatch: {written} != {target_size}")
                if patch.read(1) != b"":
                    raise ValueError("trailing data after delta terminator")
        result.flush()


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--from-apk", required=True, type=Path)
    parser.add_argument("--to-apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    size, operations = build_patch(args.from_apk, args.to_apk, args.output)
    verify_path = args.output.with_suffix(args.output.suffix + ".verify.apk")
    try:
        apply_patch(args.from_apk, args.output, verify_path)
        expected = sha256(args.to_apk)
        actual = sha256(verify_path)
        if expected != actual:
            raise RuntimeError(f"self-verification failed: expected {expected}, got {actual}")
    finally:
        verify_path.unlink(missing_ok=True)
    print(f"patch_bytes={size}")
    print(f"target_bytes={args.to_apk.stat().st_size}")
    print(f"operations={operations}")
    print(f"target_sha256={sha256(args.to_apk)}")
    print("self_verification=passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"delta generation failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
