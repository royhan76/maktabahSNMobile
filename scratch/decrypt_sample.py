import os
import hashlib
from Crypto.Cipher import AES
import zlib
import json
import struct

SECRET_KEY_STRING = b"roudlotuttholibin_secret_key"
key = hashlib.sha256(SECRET_KEY_STRING).digest()

file_path = r"E:\ANDROID\roudhotuttholibin\app\src\main\assets\books\attanbih.mai"

with open(file_path, "rb") as f:
    header = f.read(68)
    if len(header) < 68:
        print("Header too short")
        exit()
        
    magic = header[0:4]
    print("Magic:", magic)
    
    payload_hash = header[4:36]
    iv = header[36:52]
    
    meta_offset, meta_comp_len, meta_len, num_chapters = struct.unpack(">IIII", header[52:68])
    print(f"meta_offset: {meta_offset}, meta_comp_len: {meta_comp_len}, meta_len: {meta_len}, num_chapters: {num_chapters}")
    
    index_table_size = num_chapters * 12
    index_table_bytes = f.read(index_table_size)
    
    chapters_indices = []
    for i in range(num_chapters):
        offset, comp_len, orig_len = struct.unpack(">III", index_table_bytes[i*12 : (i+1)*12])
        chapters_indices.append((offset, comp_len, orig_len))
        
    # Read the rest of the file which is the encrypted payload
    encrypted_payload = f.read()

# Decrypt the payload
cipher = AES.new(key, AES.MODE_CBC, iv)
decrypted_payload = cipher.decrypt(encrypted_payload)

# Let's get the first chapter
offset, comp_len, orig_len = chapters_indices[0]
print(f"First chapter: offset={offset}, comp_len={comp_len}, orig_len={orig_len}")

comp_bytes = decrypted_payload[offset : offset + comp_len]
# decompress using zlib
decompressor = zlib.decompressobj()
bytes_decompressed = decompressor.decompress(comp_bytes)

chapter_json = json.loads(bytes_decompressed.decode("utf-8"))
print("Chapter JSON keys:", chapter_json.keys())
print("Chapter Sample:", json.dumps(chapter_json, indent=2, ensure_ascii=False)[:1000])
