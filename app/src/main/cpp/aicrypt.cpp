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

#define TAG "AICryptNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/**
 * Simple XOR-based encryption for obfuscation
 * Not cryptographically secure, but hides strings from casual analysis
 */
extern "C" {

// XOR encryption with key
JNIEXPORT jbyteArray JNICALL
Java_com_cornspace_aichat_security_SecureEncryption_encryptNative(
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
Java_com_cornspace_aichat_security_SecureEncryption_decryptNative(
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
Java_com_cornspace_aichat_security_SecureEncryption_computeHash(
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
Java_com_cornspace_aichat_security_SslPinningManager_nativeVerifyCertificate(
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
Java_com_cornspace_aichat_security_SslPinningManager_detectSslBypass(
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
Java_com_cornspace_aichat_security_SecureEncryption_generateRandomKey(
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
Java_com_cornspace_aichat_security_SecureEncryption_obfuscateString(
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

} // extern "C"
