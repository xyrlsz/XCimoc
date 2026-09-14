/*
 * QuickJS JavaScript 引擎的 Android JNI 封装
 *
 * 底层引擎：https://github.com/quickjs-ng/quickjs (MIT License, QuickJS-NG)
 *
 * 特性：
 *  - 每个 JSRuntime 相互隔离（独立堆、单线程模型）
 *  - 内存上限 / JS 调用栈上限 / 单次执行超时三重保护
 *  - 未注册 std/os 模块，脚本无法访问文件系统、进程等宿主资源
 *  - 兼容层：atob/btoa（基于内置 Uint8Array.fromBase64/toBase64）、console.log/print
 */

#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <android/log.h>

#include "quickjs.h"
#include "utf8to16.h"
#include "codec_native.h"

#define LOG_TAG "QuickJS"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define QJS_TIMEOUT_MS    (30 * 1000)         /* 单次脚本执行超时（加大，重 DOM 解析/大页面更从容） */
#define QJS_MEMORY_LIMIT  (128 * 1024 * 1024) /* 运行时内存上限 128MB */
#define QJS_STACK_SIZE    (2 * 1024 * 1024)   /* JS 调用栈上限 2MB */

typedef struct {
    int64_t deadline_ms; /* 单调时钟截止时间 */
} QJSRuntimeData;

/* 保存 JavaVM*，供 hostCall 回调回到 Java 侧（JNI_OnLoad 时填充） */
static JavaVM *g_vm = NULL;

/* ---------------- 工具 ---------------- */

static int64_t now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t) ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

/* 执行超时中断回调：返回非 0 时引擎中止执行 */
static int qjs_interrupt_handler(JSRuntime *rt, void *opaque) {
    QJSRuntimeData *data = (QJSRuntimeData *) opaque;
    return now_ms() >= data->deadline_ms;
}

/* 将标准 UTF-8 字节转成 Java String（正确处理增补平面字符/代理对）。
 * 注意：NewStringUTF 期望"修改版 UTF-8"，直接传入标准 UTF-8 会在
 * 非 BMP 字符（如 emoji）处出错，故统一走 UTF-8 -> UTF-16 转换。 */
static jstring qjs_utf8_to_jstring(JNIEnv *env, const char *utf8, size_t len) {
    if (!utf8)
        return (*env)->NewStringUTF(env, "");

    if (len == 0) {
        return (*env)->NewStringUTF(env, "");
    } else {
        jchar *utf16 = (jchar *) malloc((len + 1) * sizeof(jchar));
        jstring jstr;
        if (utf16) {
            size_t n = utf8_to_utf16(utf8, len, utf16);
            jstr = (*env)->NewString(env, utf16, (jsize) n);
            free(utf16);
        } else {
            jstr = (*env)->NewStringUTF(env, "");
        }
        return jstr;
    }
}

/* 将 JSValue 字符串化后转成 Java String */
static jstring qjs_jsvalue_to_jstring(JNIEnv *env, JSContext *ctx, JSValueConst val) {
    size_t len = 0;
    const char *utf8 = JS_ToCStringLen2(ctx, &len, val, 0);
    jstring jstr;
    if (!utf8) {
        /* ToCString 失败（如 OOM）：清异常，避免残留影响后续调用 */
        JSValue exc = JS_GetException(ctx);
        JS_FreeValue(ctx, exc);
        return (*env)->NewStringUTF(env, "");
    }
    jstr = qjs_utf8_to_jstring(env, utf8, len);
    JS_FreeCString(ctx, utf8);
    return jstr;
}

/* ---------------- console.log / print ---------------- */

static JSValue js_console_log(JSContext *ctx, JSValueConst this_val,
                              int argc, JSValueConst *argv) {
    size_t cap = 256, len = 0;
    char *buf = (char *) js_malloc(ctx, cap);
    JSValue ret = JS_UNDEFINED;
    int i;

    if (!buf)
        return JS_EXCEPTION;
    for (i = 0; i < argc; i++) {
        const char *str;
        JSValue s = JS_ToString(ctx, argv[i]);
        if (JS_IsException(s)) {
            ret = JS_EXCEPTION;
            goto done;
        }
        str = JS_ToCString(ctx, s);
        if (str) {
            size_t slen = strlen(str);
            if (len + slen + 2 > cap) {
                char *nbuf;
                cap = (len + slen + 2) * 2;
                nbuf = (char *) js_realloc(ctx, buf, cap);
                if (!nbuf) {
                    JS_FreeCString(ctx, str);
                    JS_FreeValue(ctx, s);
                    ret = JS_EXCEPTION;
                    goto done;
                }
                buf = nbuf;
            }
            if (i > 0)
                buf[len++] = ' ';
            memcpy(buf + len, str, slen);
            len += slen;
            JS_FreeCString(ctx, str);
        }
        JS_FreeValue(ctx, s);
    }
    buf[len] = '\0';
    LOGI("%s", buf);
    done:
    js_free(ctx, buf);
    return ret;
}

/* ---------------- atob / btoa 兼容层 ---------------- */
/* 基于 QuickJS 内置的 Uint8Array.fromBase64 / toBase64 实现，
   避免手写 Latin-1 字符串转换的坑。 */
static const char qjs_helpers_js[] =
        "if (typeof globalThis.atob !== 'function') {"
        "  globalThis.atob = function(b64) {"
        "    var bytes = Uint8Array.fromBase64(String(b64));"
        "    var out = '', i;"
        "    for (i = 0; i < bytes.length; i++) out += String.fromCharCode(bytes[i]);"
        "    return out;"
        "  };"
        "}"
        "if (typeof globalThis.btoa !== 'function') {"
        "  globalThis.btoa = function(bin) {"
        "    var s = String(bin), bytes = new Uint8Array(s.length), i;"
        "    for (i = 0; i < s.length; i++) bytes[i] = s.charCodeAt(i) & 0xFF;"
        "    return bytes.toBase64();"
        "  };"
        "}";

/* ---------------- hostCall 桥（JS → Java） ---------------- */

/* JS 侧调用 hostCall(name, argsJson) 回调到 Java 的
 * QuickJSEngine.onHostCall(String, String) -> String。
 * 返回值以 JS 字符串形式返回；失败/异常时返回 "null"。 */
static JSValue js_host_call(JSContext *ctx, JSValueConst this_val,
                            int argc, JSValueConst *argv) {
    JNIEnv *env = NULL;
    jstring jname = NULL, jargs = NULL, jresult = NULL;
    jclass clazz = NULL;
    jmethodID mid = NULL;
    const char *name_str = NULL, *args_str = NULL;
    const char *r = NULL;
    char *native_result = NULL;
    JSValue ret = JS_NULL;

    if (g_vm == NULL ||
        (*g_vm)->GetEnv(g_vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK)
        return JS_NULL;

    if (argc >= 1)
        name_str = JS_ToCString(ctx, argv[0]);
    if (argc >= 2)
        args_str = JS_ToCString(ctx, argv[1]);
    if (!name_str)
        goto done;

    if (args_str && qjs_native_codec(name_str, args_str, &native_result)) {
        ret = JS_NewString(ctx, native_result);
        free(native_result);
        native_result = NULL;
        goto done;
    }

    jname = (*env)->NewStringUTF(env, name_str);
    jargs = (*env)->NewStringUTF(env, args_str ? args_str : "[]");

    clazz = (*env)->FindClass(env, "com/xyrlsz/quickjs/QuickJSEngine");
    if (!clazz)
        goto done;
    mid = (*env)->GetStaticMethodID(env, clazz, "onHostCall",
                                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    if (!mid)
        goto done;
    jresult = (jstring) (*env)->CallStaticObjectMethod(env, clazz, mid, jname, jargs);
    if (jresult) {
        r = (*env)->GetStringUTFChars(env, jresult, NULL);
        if (r) {
            ret = JS_NewString(ctx, r);
            (*env)->ReleaseStringUTFChars(env, jresult, r);
        }
        (*env)->DeleteLocalRef(env, jresult);
    }
    if ((*env)->ExceptionCheck(env))
        (*env)->ExceptionClear(env);

    done:
    if (name_str)
        JS_FreeCString(ctx, name_str);
    if (args_str)
        JS_FreeCString(ctx, args_str);
    if (jname)
        (*env)->DeleteLocalRef(env, jname);
    if (jargs)
        (*env)->DeleteLocalRef(env, jargs);
    if (clazz)
        (*env)->DeleteLocalRef(env, clazz);
    return ret;
}

static void qjs_register_helpers(JSContext *ctx) {
    JSValue global, log, console, ret, host;

    global = JS_GetGlobalObject(ctx);

    log = JS_NewCFunction(ctx, js_console_log, "log", 1);
    console = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, console, "log", JS_DupValue(ctx, log));
    JS_SetPropertyStr(ctx, global, "console", console);
    JS_SetPropertyStr(ctx, global, "print", log);

    /* 安装 hostCall(name, argsJson)，供宿主 SDK 回调到 Java。
     * JS_SetPropertyStr 会消费传入的引用，故此处不再 JS_FreeValue。 */
    host = JS_NewCFunction(ctx, js_host_call, "hostCall", 2);
    JS_SetPropertyStr(ctx, global, "hostCall", host);

    JS_FreeValue(ctx, global);

    ret = JS_Eval(ctx, qjs_helpers_js, strlen(qjs_helpers_js), "<helpers>",
                  JS_EVAL_TYPE_GLOBAL);
    if (JS_IsException(ret)) {
        JSValue exc = JS_GetException(ctx);
        const char *msg = JS_ToCString(ctx, exc);
        LOGE("Failed to install atob/btoa: %s", msg ? msg : "unknown");
        if (msg)
            JS_FreeCString(ctx, msg);
        JS_FreeValue(ctx, exc);
    }
    JS_FreeValue(ctx, ret);
}

/* ---------------- JSON 桥接工具 ---------------- */

/* 重置本次执行的超时截止时间（每次 JNI 调用都重新计时） */
static void qjs_reset_deadline(JSContext *ctx) {
    JSRuntime *rt = JS_GetRuntime(ctx);
    QJSRuntimeData *data = (QJSRuntimeData *) JS_GetRuntimeOpaque(rt);
    if (data)
        data->deadline_ms = now_ms() + QJS_TIMEOUT_MS;
}

/* 将 JSValue 序列化为 JSON 字符串（js_malloc 分配，调用方 js_free 释放）。
 * undefined / 序列化异常（函数、循环引用、BigInt 等）统一输出 "null"，
 * 保证 Java 侧拿到的一定是合法 JSON 文本。 */
static char *qjs_json_stringify(JSContext *ctx, JSValueConst val, size_t *out_len) {
    JSValue json = JS_JSONStringify(ctx, val, JS_UNDEFINED, JS_UNDEFINED);
    char *s = NULL;
    size_t len = 0;

    if (JS_IsException(json)) {
        JSValue exc = JS_GetException(ctx);
        const char *msg = JS_ToCString(ctx, exc);
        LOGE("JS exception (stringify): %s", msg ? msg : "(unknown)");
        if (msg)
            JS_FreeCString(ctx, msg);
        JS_FreeValue(ctx, exc);
        return NULL;
    }

    if (JS_IsUndefined(json)) {
        JS_FreeValue(ctx, json);
        len = 4;
        s = (char *) js_malloc(ctx, len + 1);
        if (s) {
            memcpy(s, "null", len + 1);
        }
    } else {
        const char *cs = JS_ToCStringLen2(ctx, &len, json, 0);
        JS_FreeValue(ctx, json);
        if (cs) {
            /* JS_ToCStringLen2 结果需 JS_FreeCString 释放；统一转为
               js_malloc 缓冲，让调用方统一 js_free */
            char *copy = (char *) js_malloc(ctx, len + 1);
            if (copy) {
                memcpy(copy, cs, len);
                copy[len] = '\0';
            }
            JS_FreeCString(ctx, cs);
            s = copy;
        } else {
            /* ToCString 失败（如 OOM）：清异常，避免残留 */
            JSValue exc = JS_GetException(ctx);
            JS_FreeValue(ctx, exc);
        }
    }

    if (out_len)
        *out_len = s ? len : 0;
    return s;
}

/* 解析 JSON 数组字符串为 JSValue 参数数组。
 * 成功返回 argc（>=0），失败返回 -1（不区分"非法 JSON"与"非数组"）。
 * 返回的 argv 用 js_free 释放，每个元素需 JS_FreeValue。 */
static int qjs_parse_json_args(JSContext *ctx, const char *str, size_t len,
                               JSValue **out_argv) {
    JSValue arr;
    JSValue *argv = NULL;
    uint32_t i, n = 0;

    arr = JS_ParseJSON(ctx, str, len, "<args>");
    if (JS_IsException(arr)) {
        JSValue exc = JS_GetException(ctx);
        const char *msg = JS_ToCString(ctx, exc);
        LOGE("JS exception (parse args): %s", msg ? msg : "(unknown)");
        if (msg)
            JS_FreeCString(ctx, msg);
        JS_FreeValue(ctx, exc);
        return -1;
    }
    /* quickjs-ng 的 JS_IsArray 为单参（不穿透 Proxy） */
    if (!JS_IsArray(arr)) {
        JS_FreeValue(ctx, arr);
        return -1;
    }
    { /* quickjs-ng 无 JS_GetPropertyLength，改用 length 属性 */
        JSValue len_val = JS_GetPropertyStr(ctx, arr, "length");
        JS_ToUint32(ctx, &n, len_val);
        JS_FreeValue(ctx, len_val);
    }
    if (n == 0) {
        /* 空数组：跳过分配。js_malloc(ctx, 0) 在 quickjs-ng 中对 size==0
           直接返回 NULL，js_malloc 会补抛一个假的 "out of memory" 异常，
           残留 pending 异常会污染后续调用（如 BaseFunction 无参调用时
           encodeArgs 恒返回 "[]" 就会走到这里）。 */
        JS_FreeValue(ctx, arr);
        *out_argv = NULL;
        return 0;
    }
    argv = (JSValue *) js_malloc(ctx, n * sizeof(JSValue));
    if (!argv) {
        JS_FreeValue(ctx, arr);
        return -1;
    }
    for (i = 0; i < n; i++)
        argv[i] = JS_GetPropertyUint32(ctx, arr, i);
    JS_FreeValue(ctx, arr);
    *out_argv = argv;
    return (int) n;
}

/* ---------------- JNI 方法 ---------------- */

static jlong native_create_runtime(JNIEnv *env, jclass clazz) {
    JSRuntime *rt = JS_NewRuntime();
    QJSRuntimeData *data;

    if (!rt)
        return 0;
    data = (QJSRuntimeData *) calloc(1, sizeof(QJSRuntimeData));
    if (!data) {
        JS_FreeRuntime(rt);
        return 0;
    }
    data->deadline_ms = now_ms() + QJS_TIMEOUT_MS;
    JS_SetRuntimeOpaque(rt, data);
    JS_SetMemoryLimit(rt, QJS_MEMORY_LIMIT);
    JS_SetMaxStackSize(rt, QJS_STACK_SIZE);
    JS_SetInterruptHandler(rt, qjs_interrupt_handler, data);
    return (jlong) (intptr_t) rt;
}

static jlong native_create_context(JNIEnv *env, jclass clazz, jlong rt_ptr) {
    JSRuntime *rt = (JSRuntime *) (intptr_t) rt_ptr;
    JSContext *ctx;

    if (!rt)
        return 0;
    ctx = JS_NewContext(rt);
    if (!ctx)
        return 0;
    qjs_register_helpers(ctx);
    return (jlong) (intptr_t) ctx;
}

/* 求值脚本。as_json=JNI_TRUE 时结果以 JSON 序列化返回（供 Rhino 风格
 * evaluateString 使用）；否则按字符串化语义返回（兼容旧接口）。
 * var_name 非空时返回指定全局变量的值（仅字符串化模式使用）。 */
static jstring qjs_evaluate(JNIEnv *env, jlong ctx_ptr, jstring script,
                            jstring filename, jstring var_name, jboolean as_json) {
    JSContext *ctx = (JSContext *) (intptr_t) ctx_ptr;
    const char *script_str;
    const char *filename_str = "<eval>";
    jboolean filename_allocated = JNI_FALSE;
    const char *vname = NULL;
    JSValue result;
    jstring jresult = NULL;

    if (!ctx)
        return (*env)->NewStringUTF(env, "");

    /* 重置本次执行的超时截止时间 */
    qjs_reset_deadline(ctx);

    script_str = (*env)->GetStringUTFChars(env, script, NULL);
    if (!script_str)
        return (*env)->NewStringUTF(env, "");

    if (filename) {
        const char *fs = (*env)->GetStringUTFChars(env, filename, NULL);
        if (fs) {
            filename_str = fs;
            filename_allocated = JNI_TRUE;
        }
    }
    if (var_name)
        vname = (*env)->GetStringUTFChars(env, var_name, NULL);

    result = JS_Eval(ctx, script_str, strlen(script_str), filename_str,
                     JS_EVAL_TYPE_GLOBAL);

    if (JS_IsException(result)) {
        JSValue exc = JS_GetException(ctx);
        const char *msg = JS_ToCString(ctx, exc);
        LOGE("JS exception: %s", msg ? msg : "(unknown)");
        if (msg)
            JS_FreeCString(ctx, msg);
        JS_FreeValue(ctx, exc);
        JS_FreeValue(ctx, result);
        jresult = (*env)->NewStringUTF(env, as_json ? "null" : "");
        goto done;
    }

    /* 需要返回指定全局变量的值（字符串化模式） */
    if (vname != NULL) {
        JSValue global = JS_GetGlobalObject(ctx);
        JSValue prop = JS_GetPropertyStr(ctx, global, vname);
        JS_FreeValue(ctx, global);
        JS_FreeValue(ctx, result);
        if (JS_IsException(prop)) {
            JSValue exc = JS_GetException(ctx);
            const char *msg = JS_ToCString(ctx, exc);
            LOGE("JS exception (get var '%s'): %s", vname,
                 msg ? msg : "(unknown)");
            if (msg)
                JS_FreeCString(ctx, msg);
            JS_FreeValue(ctx, exc);
            JS_FreeValue(ctx, prop);
            jresult = (*env)->NewStringUTF(env, "");
            goto done;
        }
        result = prop;
    }

    if (as_json) {
        /* JSON 序列化（undefined/异常统一输出 "null"） */
        size_t json_len = 0;
        char *json_out = qjs_json_stringify(ctx, result, &json_len);
        JS_FreeValue(ctx, result);
        if (json_out) {
            jresult = qjs_utf8_to_jstring(env, json_out, json_len);
            js_free(ctx, json_out);
        } else {
            jresult = (*env)->NewStringUTF(env, "null");
        }
    } else {
        /* 结果转字符串（数组会按 Array.prototype.toString 以逗号连接，
           与旧 Rhino 的 Context.toString 行为一致） */
        JSValue str_val = JS_ToString(ctx, result);
        JS_FreeValue(ctx, result);
        if (JS_IsException(str_val)) {
            JSValue exc = JS_GetException(ctx);
            const char *msg = JS_ToCString(ctx, exc);
            LOGE("JS exception (to string): %s", msg ? msg : "(unknown)");
            if (msg)
                JS_FreeCString(ctx, msg);
            JS_FreeValue(ctx, exc);
            JS_FreeValue(ctx, str_val);
            jresult = (*env)->NewStringUTF(env, "");
            goto done;
        }
        jresult = qjs_jsvalue_to_jstring(env, ctx, str_val);
        JS_FreeValue(ctx, str_val);
    }

    done:
    if (jresult == NULL)
        jresult = (*env)->NewStringUTF(env, as_json ? "null" : "");
    (*env)->ReleaseStringUTFChars(env, script, script_str);
    if (filename_allocated)
        (*env)->ReleaseStringUTFChars(env, filename, filename_str);
    if (vname)
        (*env)->ReleaseStringUTFChars(env, var_name, vname);
    return jresult;
}

static jstring native_evaluate(JNIEnv *env, jclass clazz, jlong ctx_ptr,
                               jstring script, jstring filename, jstring var_name) {
    return qjs_evaluate(env, ctx_ptr, script, filename, var_name, JNI_FALSE);
}

static jstring native_evaluate_json(JNIEnv *env, jclass clazz, jlong ctx_ptr,
                                    jstring script, jstring filename) {
    return qjs_evaluate(env, ctx_ptr, script, filename, NULL, JNI_TRUE);
}

/* 判断全局对象上 name 是否为可调用函数 */
static jboolean native_has_global_function(JNIEnv *env, jclass clazz, jlong ctx_ptr,
                                           jstring name) {
    JSContext *ctx = (JSContext *) (intptr_t) ctx_ptr;
    const char *name_str = NULL;
    JSValue global, prop;
    jboolean result = JNI_FALSE;

    if (!ctx || !name)
        return JNI_FALSE;

    qjs_reset_deadline(ctx);

    name_str = (*env)->GetStringUTFChars(env, name, NULL);
    if (!name_str)
        return JNI_FALSE;

    global = JS_GetGlobalObject(ctx);
    prop = JS_GetPropertyStr(ctx, global, name_str);
    result = JS_IsFunction(ctx, prop) ? JNI_TRUE : JNI_FALSE;
    JS_FreeValue(ctx, prop);
    JS_FreeValue(ctx, global);

    (*env)->ReleaseStringUTFChars(env, name, name_str);
    return result;
}

/* 判断全局对象上是否存在 name 属性（含值为 undefined 的属性） */
static jboolean native_has_global(JNIEnv *env, jclass clazz, jlong ctx_ptr,
                                  jstring name) {
    JSContext *ctx = (JSContext *) (intptr_t) ctx_ptr;
    const char *name_str = NULL;
    JSValue global;
    int present;
    jboolean result = JNI_FALSE;

    if (!ctx || !name)
        return JNI_FALSE;

    qjs_reset_deadline(ctx);

    name_str = (*env)->GetStringUTFChars(env, name, NULL);
    if (!name_str)
        return JNI_FALSE;

    global = JS_GetGlobalObject(ctx);
    { /* quickjs-ng 无 JS_HasPropertyStr，用 atom 版 */
        JSAtom atom = JS_NewAtom(ctx, name_str);
        present = JS_HasProperty(ctx, global, atom);
        JS_FreeAtom(ctx, atom);
    }
    result = (present > 0) ? JNI_TRUE : JNI_FALSE;
    if (present < 0)
        JS_GetException(ctx); /* 清除异常 */
    JS_FreeValue(ctx, global);

    (*env)->ReleaseStringUTFChars(env, name, name_str);
    return result;
}

/* 调用全局对象上的函数。args_json 为 JSON 数组文本，结果以 JSON 序列化返回。
 * 函数不存在 / 调用异常 / 序列化失败时返回 "null"。 */
static jstring native_call_function(JNIEnv *env, jclass clazz, jlong ctx_ptr,
                                    jstring name, jstring args_json) {
    JSContext *ctx = (JSContext *) (intptr_t) ctx_ptr;
    const char *name_str = NULL;
    const char *args_str = NULL;
    JSValue global = JS_UNDEFINED, func = JS_UNDEFINED, result;
    JSValue *argv = NULL;
    int argc = 0, i;
    char *json_out = NULL;
    size_t json_len = 0;
    jstring jresult = NULL;

    if (!ctx || !name)
        return (*env)->NewStringUTF(env, "null");

    qjs_reset_deadline(ctx);

    name_str = (*env)->GetStringUTFChars(env, name, NULL);
    if (!name_str)
        return (*env)->NewStringUTF(env, "null");
    if (args_json)
        args_str = (*env)->GetStringUTFChars(env, args_json, NULL);

    global = JS_GetGlobalObject(ctx);
    func = JS_GetPropertyStr(ctx, global, name_str);

    if (!JS_IsFunction(ctx, func)) {
        LOGE("callFunction: '%s' is not a function", name_str);
        goto done;
    }

    if (args_str) {
        argc = qjs_parse_json_args(ctx, args_str, strlen(args_str), &argv);
        if (argc < 0)
            argc = 0; /* 参数 JSON 解析失败按无参处理 */
    }

    result = JS_Call(ctx, func, global, argc, argv);
    if (JS_IsException(result)) {
        JSValue exc = JS_GetException(ctx);
        const char *msg = JS_ToCString(ctx, exc);
        LOGE("JS exception (call '%s'): %s", name_str, msg ? msg : "(unknown)");
        if (msg)
            JS_FreeCString(ctx, msg);
        JS_FreeValue(ctx, exc);
        JS_FreeValue(ctx, result);
        goto done;
    }

    json_out = qjs_json_stringify(ctx, result, &json_len);
    JS_FreeValue(ctx, result);

    done:
    if (argv) {
        for (i = 0; i < argc; i++)
            JS_FreeValue(ctx, argv[i]);
        js_free(ctx, argv);
    }
    JS_FreeValue(ctx, func);
    JS_FreeValue(ctx, global);

    if (json_out) {
        jresult = qjs_utf8_to_jstring(env, json_out, json_len);
        js_free(ctx, json_out);
    } else {
        jresult = (*env)->NewStringUTF(env, "null");
    }
    (*env)->ReleaseStringUTFChars(env, name, name_str);
    if (args_str)
        (*env)->ReleaseStringUTFChars(env, args_json, args_str);
    return jresult;
}

/* 读取全局变量，JSON 序列化返回（undefined/异常 → "null"） */
static jstring native_get_global_json(JNIEnv *env, jclass clazz, jlong ctx_ptr,
                                      jstring name) {
    JSContext *ctx = (JSContext *) (intptr_t) ctx_ptr;
    const char *name_str = NULL;
    JSValue global, prop;
    char *json_out = NULL;
    size_t json_len = 0;
    jstring jresult = NULL;

    if (!ctx || !name)
        return (*env)->NewStringUTF(env, "null");

    qjs_reset_deadline(ctx);

    name_str = (*env)->GetStringUTFChars(env, name, NULL);
    if (!name_str)
        return (*env)->NewStringUTF(env, "null");

    global = JS_GetGlobalObject(ctx);
    prop = JS_GetPropertyStr(ctx, global, name_str);
    if (JS_IsException(prop)) {
        JSValue exc = JS_GetException(ctx);
        const char *msg = JS_ToCString(ctx, exc);
        LOGE("JS exception (get global '%s'): %s", name_str,
             msg ? msg : "(unknown)");
        if (msg)
            JS_FreeCString(ctx, msg);
        JS_FreeValue(ctx, exc);
        JS_FreeValue(ctx, prop);
        JS_FreeValue(ctx, global);
        (*env)->ReleaseStringUTFChars(env, name, name_str);
        return (*env)->NewStringUTF(env, "null");
    }

    json_out = qjs_json_stringify(ctx, prop, &json_len);
    JS_FreeValue(ctx, prop);
    JS_FreeValue(ctx, global);

    if (json_out) {
        jresult = qjs_utf8_to_jstring(env, json_out, json_len);
        js_free(ctx, json_out);
    } else {
        jresult = (*env)->NewStringUTF(env, "null");
    }
    (*env)->ReleaseStringUTFChars(env, name, name_str);
    return jresult;
}

/* 把 JSON 值写入全局变量。成功返回 JNI_TRUE。 */
static jboolean native_set_global(JNIEnv *env, jclass clazz, jlong ctx_ptr,
                                  jstring name, jstring value_json) {
    JSContext *ctx = (JSContext *) (intptr_t) ctx_ptr;
    const char *name_str = NULL;
    const char *value_str = NULL;
    JSValue global, val;
    int ret;

    if (!ctx || !name || !value_json)
        return JNI_FALSE;

    qjs_reset_deadline(ctx);

    name_str = (*env)->GetStringUTFChars(env, name, NULL);
    if (!name_str)
        return JNI_FALSE;
    value_str = (*env)->GetStringUTFChars(env, value_json, NULL);
    if (!value_str) {
        (*env)->ReleaseStringUTFChars(env, name, name_str);
        return JNI_FALSE;
    }

    val = JS_ParseJSON(ctx, value_str, strlen(value_str), "<value>");
    if (JS_IsException(val)) {
        JSValue exc = JS_GetException(ctx);
        const char *msg = JS_ToCString(ctx, exc);
        LOGE("JS exception (parse value for '%s'): %s", name_str,
             msg ? msg : "(unknown)");
        if (msg)
            JS_FreeCString(ctx, msg);
        JS_FreeValue(ctx, exc);
        JS_FreeValue(ctx, val);
        (*env)->ReleaseStringUTFChars(env, name, name_str);
        (*env)->ReleaseStringUTFChars(env, value_json, value_str);
        return JNI_FALSE;
    }

    global = JS_GetGlobalObject(ctx);
    ret = JS_SetPropertyStr(ctx, global, name_str, val); /* 成功时吃掉 val */
    if (ret < 0)
        JS_GetException(ctx); /* 清除异常 */
    JS_FreeValue(ctx, global);

    (*env)->ReleaseStringUTFChars(env, name, name_str);
    (*env)->ReleaseStringUTFChars(env, value_json, value_str);
    return ret > 0 ? JNI_TRUE : JNI_FALSE;
}

static void native_free_context(JNIEnv *env, jclass clazz, jlong ctx_ptr) {
    JSContext *ctx = (JSContext *) (intptr_t) ctx_ptr;
    if (ctx)
        JS_FreeContext(ctx);
}

static void native_free_runtime(JNIEnv *env, jclass clazz, jlong rt_ptr) {
    JSRuntime *rt = (JSRuntime *) (intptr_t) rt_ptr;
    if (rt) {
        QJSRuntimeData *data = (QJSRuntimeData *) JS_GetRuntimeOpaque(rt);
        JS_FreeRuntime(rt);
        free(data);
    }
}

/* ---------------- 注册 ---------------- */

static const JNINativeMethod g_methods[] = {
        {"nativeCreateRuntime",     "()J",
                (void *) native_create_runtime},
        {"nativeCreateContext",     "(J)J",
                (void *) native_create_context},
        {"nativeEvaluate",          "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                (void *) native_evaluate},
        {"nativeEvaluateJson",      "(JLjava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                (void *) native_evaluate_json},
        {"nativeHasGlobalFunction", "(JLjava/lang/String;)Z",
                (void *) native_has_global_function},
        {"nativeHasGlobal",         "(JLjava/lang/String;)Z",
                (void *) native_has_global},
        {"nativeCallFunction",      "(JLjava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                (void *) native_call_function},
        {"nativeGetGlobalJson",     "(JLjava/lang/String;)Ljava/lang/String;",
                (void *) native_get_global_json},
        {"nativeSetGlobal",         "(JLjava/lang/String;Ljava/lang/String;)Z",
                (void *) native_set_global},
        {"nativeFreeContext",       "(J)V",
                (void *) native_free_context},
        {"nativeFreeRuntime",       "(J)V",
                (void *) native_free_runtime},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    jclass clazz;

    g_vm = vm;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK)
        return JNI_ERR;
    clazz = (*env)->FindClass(env, "com/xyrlsz/quickjs/QuickJSEngine");
    if (!clazz)
        return JNI_ERR;
    if ((*env)->RegisterNatives(env, clazz, g_methods,
                                (int) (sizeof(g_methods) / sizeof(g_methods[0]))) != JNI_OK)
        return JNI_ERR;
    return JNI_VERSION_1_6;
}
