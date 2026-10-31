#include <jni.h>
#include <android/log.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "libretro.h"

#define LOG_TAG "SegaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define FRAME_PIXELS (1024 * 576)
#define AUDIO_FRAMES 16384

static pthread_mutex_t core_mutex = PTHREAD_MUTEX_INITIALIZER;
static atomic_uint input_mask = 0;
static bool initialized = false;
static bool loaded = false;
static uint16_t frame[FRAME_PIXELS];
static int16_t audio[AUDIO_FRAMES * 2];
static size_t audio_count = 0;
static unsigned frame_width = 0, frame_height = 0;
static uint64_t frame_serial = 0;
static double fps = 60.0, sample_rate = 44100.0;
static unsigned max_width = 348, max_height = 288;
#ifdef SEGA_ENABLE_TEST_API
static atomic_uint input_queries[16] = {};
static atomic_int last_mask = 0;
#endif

static void throw_env(JNIEnv *env, const char *type, const char *message) {
    jclass cls = (*env)->FindClass(env, type);
    if (cls) (*env)->ThrowNew(env, cls, message);
}
static void video_callback(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data || width == 0 || height == 0) return;
    if (width > 1024 || height > 576) return;
    const uint8_t *src = data;
    for (unsigned y = 0; y < height; ++y)
        memcpy(frame + y * width, src + y * pitch, width * sizeof(uint16_t));
    frame_width = width;
    frame_height = height;
    ++frame_serial;
}
static void audio_callback(const int16_t *data, size_t frames) {
    size_t count = frames * 2;
    if (count > AUDIO_FRAMES * 2 - audio_count) count = AUDIO_FRAMES * 2 - audio_count;
    if (data && count) memcpy(audio + audio_count, data, count * sizeof(int16_t));
    audio_count += count;
}
static size_t audio_batch_callback(const int16_t *data, size_t frames) {
    audio_callback(data, frames);
    return frames;
}
static void audio_size_callback(size_t *written, size_t frames) {
    size_t available = audio_count / 2;
    *written = frames < available ? frames : available;
}
static bool environment_callback(unsigned cmd, void *data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            return *(const enum retro_pixel_format *)data == RETRO_PIXEL_FORMAT_RGB565;
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *(bool *)data = true; return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            struct retro_variable *var = data;
            var->value = NULL;
            // Keep the pinned upstream defaults; video filtering belongs to the frontend.
            return false;
        }
        case RETRO_ENVIRONMENT_SET_VARIABLES:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_INTL:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
        case RETRO_ENVIRONMENT_SET_ROTATION:
            return true;
        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            *(unsigned *)data = RETRO_LANGUAGE_ENGLISH; return true;
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            struct retro_log_callback *cb = data;
            cb->log = NULL; return false;
        }
        case RETRO_ENVIRONMENT_SHUTDOWN:
            return false;
        default: return false;
    }
}
static int16_t input_callback(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port != 0 || index != 0 || id >= 16 ||
        (device != RETRO_DEVICE_JOYPAD && device != RETRO_DEVICE_NONE)) return 0;
    unsigned mask = atomic_load_explicit(&input_mask, memory_order_relaxed);
#ifdef SEGA_ENABLE_TEST_API
    atomic_fetch_add(&input_queries[id], 1);
    atomic_store(&last_mask, (int)mask);
#endif
    return (mask & (1u << id)) ? 1 : 0;
}
static void input_poll_callback(void) {}

JNIEXPORT jboolean JNICALL Java_com_midoali_atari_core_SegaCore_nativeLoad(
        JNIEnv *env, jclass cls, jstring rom_path, jboolean six_button) {
    (void)cls;
    const char *path = (*env)->GetStringUTFChars(env, rom_path, NULL);
    if (!path) return JNI_FALSE;
    pthread_mutex_lock(&core_mutex);
    bool ok = false;
    if (loaded) goto done;
    struct retro_system_info info = {0};
    retro_get_system_info(&info);
    LOGI("Core %s", info.library_version);
    retro_set_environment(environment_callback);
    retro_set_video_refresh(video_callback);
    retro_set_audio_sample_batch(audio_batch_callback);
    retro_set_audio_sample(NULL);
    retro_set_input_poll(input_poll_callback);
    retro_set_input_state(input_callback);
    retro_init();
    initialized = true;
    struct retro_game_info game = {0};
    game.path = path;
    if (!retro_load_game(&game)) {
        LOGE("Core rejected ROM");
        goto done;
    }
    loaded = true;
    struct retro_system_av_info av = {0};
    retro_get_system_av_info(&av);
    fps = av.timing.fps > 0 ? av.timing.fps : 60;
    sample_rate = av.timing.sample_rate > 0 ? av.timing.sample_rate : 44100;
    max_width = av.geometry.max_width;
    max_height = av.geometry.max_height;
    if (max_width > 1024 || max_height > 576) {
        LOGE("Unsupported geometry %ux%u", max_width, max_height);
        goto done;
    }
    unsigned pad = RETRO_DEVICE_SUBCLASS(RETRO_DEVICE_JOYPAD, six_button ? 1 : 0);
    retro_set_controller_port_device(0, pad);
    retro_set_controller_port_device(1, RETRO_DEVICE_NONE);
    frame_serial = 0;
    frame_width = frame_height = 0;
    atomic_store(&input_mask, 0);
    ok = true;
done:
    if (!ok && initialized) {
        if (loaded) retro_unload_game();
        retro_deinit();
        initialized = loaded = false;
    }
    pthread_mutex_unlock(&core_mutex);
    (*env)->ReleaseStringUTFChars(env, rom_path, path);
    if (!ok) throw_env(env, "java/io/IOException", "Genesis core could not load this ROM");
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_com_midoali_atari_core_SegaCore_nativeRun(
        JNIEnv *env, jclass cls, jshortArray output_audio, jobject output_video, jintArray metadata) {
    (void)cls;
    pthread_mutex_lock(&core_mutex);
    jint written = -1;
    if (!loaded) goto done;
    jshort *audio_out = (*env)->GetShortArrayElements(env, output_audio, NULL);
    uint8_t *video_out = (*env)->GetDirectBufferAddress(env, output_video);
    jint *meta = (*env)->GetIntArrayElements(env, metadata, NULL);
    jsize audio_capacity = (*env)->GetArrayLength(env, output_audio);
    jlong video_capacity = (*env)->GetDirectBufferCapacity(env, output_video);
    if (!audio_out || !video_out || !meta || (*env)->GetArrayLength(env, metadata) < 5 ||
        video_capacity < (jlong)max_width * max_height * 2 || audio_capacity < AUDIO_FRAMES * 2) {
        if (audio_out) (*env)->ReleaseShortArrayElements(env, output_audio, audio_out, JNI_ABORT);
        if (meta) (*env)->ReleaseIntArrayElements(env, metadata, meta, JNI_ABORT);
        pthread_mutex_unlock(&core_mutex);
        throw_env(env, "java/lang/IllegalArgumentException", "Invalid emulator output buffers");
        return -1;
    }
    audio_count = 0;
    retro_run();
    size_t count = audio_count;
    memcpy(audio_out, audio, count * sizeof(int16_t));
    size_t video_bytes = (size_t)frame_width * frame_height * 2;
    if (video_bytes) memcpy(video_out, frame, video_bytes);
    meta[0] = (jint)frame_width;
    meta[1] = (jint)frame_height;
    meta[2] = (jint)(frame_serial & 0x7fffffff);
    meta[3] = (jint)count;
    meta[4] = (jint)(atomic_load(&input_mask));
    written = (jint)(count / 2);
    (*env)->ReleaseShortArrayElements(env, output_audio, audio_out, 0);
    (*env)->ReleaseIntArrayElements(env, metadata, meta, 0);
done:
    pthread_mutex_unlock(&core_mutex);
    return written;
}

JNIEXPORT void JNICALL Java_com_midoali_atari_core_SegaCore_nativeSetInput(
        JNIEnv *env, jclass cls, jint mask) {
    (void)env; (void)cls;
    atomic_store_explicit(&input_mask, (unsigned)mask & 0xffff, memory_order_relaxed);
}
JNIEXPORT void JNICALL Java_com_midoali_atari_core_SegaCore_nativeClose(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    pthread_mutex_lock(&core_mutex);
    atomic_store(&input_mask, 0);
    if (loaded) retro_unload_game();
    if (initialized) retro_deinit();
    loaded = initialized = false;
    pthread_mutex_unlock(&core_mutex);
}
JNIEXPORT jdouble JNICALL Java_com_midoali_atari_core_SegaCore_nativeFps(JNIEnv *env, jclass cls) {
    (void)env; (void)cls; return fps;
}
JNIEXPORT jint JNICALL Java_com_midoali_atari_core_SegaCore_nativeSampleRate(JNIEnv *env, jclass cls) {
    (void)env; (void)cls; return (jint)sample_rate;
}
JNIEXPORT jint JNICALL Java_com_midoali_atari_core_SegaCore_nativeSaveSize(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    pthread_mutex_lock(&core_mutex);
    size_t size = loaded ? retro_get_memory_size(RETRO_MEMORY_SAVE_RAM) : 0;
    pthread_mutex_unlock(&core_mutex);
    return (jint)size;
}
JNIEXPORT jboolean JNICALL Java_com_midoali_atari_core_SegaCore_nativePersistSave(
        JNIEnv *env, jclass cls, jstring save_path, jboolean restore) {
    (void)cls;
    const char *path = (*env)->GetStringUTFChars(env, save_path, NULL);
    if (!path) return JNI_FALSE;
    pthread_mutex_lock(&core_mutex);
    bool ok = false;
    void *data = loaded ? retro_get_memory_data(RETRO_MEMORY_SAVE_RAM) : NULL;
    size_t size = loaded ? retro_get_memory_size(RETRO_MEMORY_SAVE_RAM) : 0;
    if (data && size > 0 && size <= 1024 * 1024) {
        FILE *file = fopen(path, restore ? "rb" : "wb");
        if (file) {
            ok = restore ? fread(data, 1, size, file) == size : fwrite(data, 1, size, file) == size;
            if (fclose(file) != 0) ok = false;
        }
    }
    pthread_mutex_unlock(&core_mutex);
    (*env)->ReleaseStringUTFChars(env, save_path, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jint JNICALL Java_com_midoali_atari_core_SegaCore_nativeTestInput(
        JNIEnv *env, jclass cls, jint id) {
    (void)env; (void)cls; (void)id;
#ifdef SEGA_ENABLE_TEST_API
    if (id == -1) return atomic_load(&last_mask);
    if (id >= 0 && id < 16) return (jint)atomic_load(&input_queries[id]);
#endif
    return -1;
}
JNIEXPORT jbyteArray JNICALL Java_com_midoali_atari_core_SegaCore_nativeTestRam(
        JNIEnv *env, jclass cls, jint offset, jint length) {
    (void)cls;
#ifdef SEGA_ENABLE_TEST_API
    pthread_mutex_lock(&core_mutex);
    uint8_t *ram = loaded ? retro_get_memory_data(RETRO_MEMORY_SYSTEM_RAM) : NULL;
    size_t size = loaded ? retro_get_memory_size(RETRO_MEMORY_SYSTEM_RAM) : 0;
    jbyteArray result = NULL;
    if (ram && offset >= 0 && length > 0 && length <= 65536 && (size_t)offset + length <= size) {
        result = (*env)->NewByteArray(env, length);
        if (result) (*env)->SetByteArrayRegion(env, result, 0, length, (const jbyte *)(ram + offset));
    }
    pthread_mutex_unlock(&core_mutex);
    return result;
#else
    (void)env; (void)offset; (void)length; return NULL;
#endif
}
