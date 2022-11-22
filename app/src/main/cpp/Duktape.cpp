#include <jni.h>
#include "duktape.h"

#ifndef DUKTAPE_H_H_H
#define DUKTAPE_H_H_H

class Engine {
    Engine() {

    }
};

#endif //DUKTAPE_H_H_H

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_exoplayer_Youtube_runScript(JNIEnv *env, jclass clazz, jstring code, jlong ptr) {


    duk_context *ctx = (duk_context*) ptr;

    const char* nativeString = env->GetStringUTFChars(code, 0);
    duk_eval_string(ctx, nativeString);
    env->ReleaseStringUTFChars(code, nativeString);

    jstring str = env->NewStringUTF(duk_get_string(ctx, -1));
    return str;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_exoplayer_Youtube_initEngine(JNIEnv *env, jclass clazz) {
    duk_context *ctx = duk_create_heap_default();

    return (jlong) ctx;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_example_exoplayer_Youtube_closeEngine(JNIEnv *env, jclass clazz, jlong ptr) {
    duk_context *ctx = (duk_context*) ptr;
    duk_destroy_heap(ctx);
}
extern "C"
JNIEXPORT jlong JNICALL
Java_com_jschartner_youtube_Youtube_initEngine(JNIEnv *env, jclass clazz) {
    duk_context *ctx = duk_create_heap_default();

    return (jlong) ctx;
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_jschartner_youtube_Youtube_runScript(JNIEnv *env, jclass clazz, jstring code,
                                              jlong pointer) {
    duk_context *ctx = (duk_context*) pointer;

    const char* nativeString = env->GetStringUTFChars(code, 0);
    duk_eval_string(ctx, nativeString);
    env->ReleaseStringUTFChars(code, nativeString);

    jstring str = env->NewStringUTF(duk_get_string(ctx, -1));
    return str;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_jschartner_youtube_Youtube_closeEngine(JNIEnv *env, jclass clazz, jlong pointer) {
    duk_context *ctx = (duk_context*) pointer;
    duk_destroy_heap(ctx);
}