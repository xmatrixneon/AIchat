#!/usr/bin/env python3
"""
XOR Encryption Generator for Native Library

Usage:
    python3 generate_xor.py "https://api.example.com"
"""

import sys

XOR_KEY = "AIChatSecretKey2024"


def xor_encrypt(text: str, key: str) -> bytes:
    """Encrypt text using XOR cipher"""
    encrypted = []
    for i, char in enumerate(text.encode('utf-8')):
        encrypted.append(char ^ ord(key[i % len(key)]))
    return bytes(encrypted)


def to_cpp_array(data: bytes) -> str:
    """Convert bytes to C++ array format"""
    hex_values = [f"0x{b:02X}" for b in data]
    return "{\n    " + ", ".join(hex_values) + "\n}"


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 generate_xor.py <your_url>")
        print("Example: python3 generate_xor.py https://api.example.com")
        sys.exit(1)

    url = sys.argv[1]
    encrypted = xor_encrypt(url, XOR_KEY)

    print(f"// Original: {url}")
    print(f"const char URL_ENC[] = {to_cpp_array(encrypted)};")
