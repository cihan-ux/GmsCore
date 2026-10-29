#!/usr/bin/env python3
"""Reproduce the modified Gspace DEX in RAM without modifying the installed app."""
import sys, zipfile, pathlib, struct, zlib, hashlib
from io import BytesIO

original_hash = "3596977526bfd45da5c8cdca2f72894fd8dca10bb59e73dcee9da236381fb067"
modified_hash = "943b8697a03162e6fabf5099d204b812c5ae56527990ee4def8faaa15aee3bba"
if len(sys.argv) != 2:
    sys.exit("Usage: python3 verify_delta.py Gspace-original.apk")
with zipfile.ZipFile(sys.argv[1]) as archive:
    dex = bytearray(archive.read('classes.dex'))
sha = lambda x: hashlib.sha256(x).hexdigest()
assert sha(dex) == original_hash, 'Original APK build does not match'
compressed = pathlib.Path(__file__).parent/'patches/src/main/resources/gspace-233.delta.zlib'
stream = BytesIO(zlib.decompress(compressed.read_bytes()))
count = struct.unpack('>I',stream.read(4))[0]
for i in range(count):
    index, val = struct.unpack('>IB',stream.read(5))
    dex[index] = val
assert stream.read() == b''
dex[12:32] = hashlib.sha1(dex[32:]).digest()
dex[8:12] = struct.pack('<I',zlib.adler32(dex[12:]) & 0xffffffff)
assert sha(dex) == modified_hash, 'Patched DEX checksum differs'
print('PASS: Gspace patched DEX matches the working NoAds build,',count,'records.')
