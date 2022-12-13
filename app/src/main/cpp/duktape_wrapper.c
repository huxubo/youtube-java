//
// Created by Justin on 13.12.2022.
//

#include <jni.h>
#include "duktape.h"

JNIEXPORT jstring JNICALL
Java_com_jschartner_youtube_Youtube_runScript(JNIEnv *env, jclass clazz, jstring code, jlong ptr) {
    duk_context *ctx = (duk_context*) ptr;

    const char* nativeString = (*env)->GetStringUTFChars(env, code, 0);
    duk_eval_string(ctx, nativeString);
    (*env)->ReleaseStringUTFChars(env, code, nativeString);

    jstring str = (*env)->NewStringUTF(env, duk_get_string(ctx, -1));
    return str;
}

JNIEXPORT jlong JNICALL
Java_com_jschartner_youtube_Youtube_initEngine(JNIEnv *env, jclass clazz) {
    duk_context *ctx = duk_create_heap_default();
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_com_jschartner_youtube_Youtube_closeEngine(JNIEnv *env, jclass clazz, jlong ptr) {
    duk_context *ctx = (duk_context*) ptr;
    duk_destroy_heap(ctx);
}

