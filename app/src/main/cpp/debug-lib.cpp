//
// Created by jswaney on 4/1/21.
//

#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring

JNICALL
Java_io_crayfis_android_server_CFConfig_getSecretSalt(JNIEnv *env, jclass object) {
    std::string salt;
    return env->NewStringUTF(salt.c_str());
}