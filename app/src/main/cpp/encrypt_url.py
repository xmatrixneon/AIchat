#!/usr/bin/env python3
import sys

KEY_PATTERN = [0x7F, 0xA5, 0x3C, 0x9D]
KEY_LEN = 4

def encrypt_url(url):
    """XOR encrypt a URL and generate C++ array"""
    encrypted = []
    for i, char in enumerate(url):
        encrypted.append(ord(char) ^ KEY_PATTERN[i % KEY_LEN])
    
    # Generate C++ array
    c_array = ", ".join(f"0x{b:02X}" for b in encrypted)
    print(f"// \"{url}\"")
    print(f"constexpr uint8_t ENC[] = {{")
    print(f"    {c_array}")
    print(f"}};")
    print(f"constexpr size_t LEN = {len(url)};")
    
    # Verify by decrypting
    decrypted = ""
    for i, b in enumerate(encrypted):
        decrypted += chr(b ^ KEY_PATTERN[i % KEY_LEN])
    print(f"\nVerified: {decrypted}")
    
    return encrypted

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: encrypt_url.py <url>")
        sys.exit(1)
    
    encrypt_url(sys.argv[1])
