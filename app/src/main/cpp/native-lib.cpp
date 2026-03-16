#include <jni.h>
#include <string>
#include <cstring>

// XOR encryption key
const char XOR_KEY[] = "AIChatSecretKey2024";
const size_t XOR_KEY_LEN = sizeof(XOR_KEY) - 1;

// XOR encryption/decryption
std::string xor_decrypt(const char* encrypted, size_t len) {
    std::string result;
    result.reserve(len);

    for (size_t i = 0; i < len; i++) {
        result += encrypted[i] ^ XOR_KEY[i % XOR_KEY_LEN];
    }

    return result;
}

// Encrypted URLs (XOR encrypted)
const char API_BASE_URL_ENC[] = {
    0x29, 0x3D, 0x37, 0x18, 0x12, 0x4E, 0x7C, 0x4A, 0x02, 0x02, 0x0C, 0x5A,
    0x28, 0x04, 0x0D, 0x46, 0x49, 0x41, 0x59, 0x32, 0x67, 0x30, 0x00, 0x0E, 0x04
};

const char WEBVIEW_URL_ENC[] = {
    0x29, 0x3D, 0x37, 0x18, 0x12, 0x4E, 0x7C, 0x4A, 0x05, 0x01, 0x0E,
    0x1D, 0x25, 0x01, 0x10, 0x53, 0x1E, 0x51, 0x5B, 0x2C, 0x66, 0x33,
    0x09, 0x13, 0x00, 0x3D, 0x00, 0x11, 0x5D, 0x0F, 0x1B, 0x22, 0x0B,
    0x54, 0x47, 0x43, 0x1D, 0x17, 0x27, 0x26, 0x31, 0x05
};

extern "C" JNIEXPORT jstring JNICALL
Java_com_cornspace_aichat_util_SecretConfig_getServerUrl(
    JNIEnv* env,
    jobject /* this */) {

    std::string decrypted = xor_decrypt(API_BASE_URL_ENC, sizeof(API_BASE_URL_ENC));
    return env->NewStringUTF(decrypted.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_cornspace_aichat_util_SecretConfig_getWebViewUrl(
    JNIEnv* env,
    jobject /* this */) {

    std::string decrypted = xor_decrypt(WEBVIEW_URL_ENC, sizeof(WEBVIEW_URL_ENC));
    return env->NewStringUTF(decrypted.c_str());
}
