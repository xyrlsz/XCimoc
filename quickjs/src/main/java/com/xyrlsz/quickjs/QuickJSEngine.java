package com.xyrlsz.quickjs;

/**
 * QuickJS 引擎的 JNI 封装（独立 Android 库）。
 * <p>
 * 底层引擎为 {@code https://github.com/quickjs-ng/quickjs}（MIT License）。
 * 通过 git 子模块固定提交。
 * 每个实例拥有独立的 JSRuntime + JSContext（堆、全局对象互相隔离）。
 * QuickJS 的 Runtime 非线程安全：同一个实例不要跨线程使用；
 * 需要并发时请为每个线程创建独立实例。
 * <p>
 * 安全特性（由 C 层保证）：
 * <ul>
 *     <li>未注册 std/os 模块，脚本无法访问文件系统、进程等宿主资源</li>
 *     <li>内存上限 {@link #DEFAULT_MEMORY_LIMIT} 字节、
 *         JS 调用栈上限 {@link #DEFAULT_STACK_SIZE} 字节、
 *         单次执行超时 {@link #DEFAULT_TIMEOUT_MS} 毫秒</li>
 * </ul>
 * 上述三个限制可以在创建实例前通过 {@link #setLimits(long, long, long)} 调整，
 * 也可以直接使用带参数的构造函数为单个实例指定。
 */
public final class QuickJSEngine {

    /* ---------------- 资源限制默认值 ---------------- */

    /**
     * 单次脚本执行超时（毫秒）。
     */
    public static final long DEFAULT_TIMEOUT_MS = 30_000L;

    /**
     * 运行时内存上限：128MB。
     */
    public static final long DEFAULT_MEMORY_LIMIT = 128L * 1024 * 1024;

    /**
     * JS 调用栈上限：2MB。
     */
    public static final long DEFAULT_STACK_SIZE = 2L * 1024 * 1024;

    /* ---------------- 资源限制当前生效值 ---------------- */

    private static volatile long sTimeoutMs = DEFAULT_TIMEOUT_MS;
    private static volatile long sMemoryLimit = DEFAULT_MEMORY_LIMIT;
    private static volatile long sStackSize = DEFAULT_STACK_SIZE;

    /* ---------------- 宿主回调桥 ---------------- */

    private static volatile HostBridge sHostBridge;

    static {
        System.loadLibrary("quickjs_jni");
    }

    /* ---------------- 实例字段 ---------------- */

    private long runtimePtr;
    private long contextPtr;

    /* ---------------- 构造 ---------------- */

    /**
     * 使用显式资源限制创建引擎。
     * 任一参数 {@code <= 0} 时由 C 层回退到编译期默认值
     * （{@link #DEFAULT_TIMEOUT_MS} / {@link #DEFAULT_MEMORY_LIMIT} / {@link #DEFAULT_STACK_SIZE}）。
     *
     * @param timeoutMs   单次脚本执行超时（毫秒）
     * @param memoryLimit 运行时内存上限（字节）
     * @param stackSize   JS 调用栈上限（字节）
     */
    public QuickJSEngine(long timeoutMs, long memoryLimit, long stackSize) {
        runtimePtr = nativeCreateRuntime(timeoutMs, memoryLimit, stackSize);
        if (runtimePtr == 0) {
            throw new RuntimeException("Failed to create QuickJS runtime");
        }
        contextPtr = nativeCreateContext(runtimePtr);
        if (contextPtr == 0) {
            nativeFreeRuntime(runtimePtr);
            runtimePtr = 0;
            throw new RuntimeException("Failed to create QuickJS context");
        }
    }

    /**
     * 使用当前生效的全局默认值创建引擎。
     * 全局默认值可通过 {@link #setLimits(long, long, long)} 修改，
     * 构造时对三个值做一次快照，避免构造期间被其他线程改掉。
     */
    public QuickJSEngine() {
        this(sTimeoutMs, sMemoryLimit, sStackSize);
    }

    /* ---------------- 资源限制配置 ---------------- */

    /**
     * 设置后续创建实例时使用的资源限制。对已创建实例不生效。
     *
     * @param timeoutMs   单次脚本执行超时（毫秒）；{@code <= 0} 时使用默认值
     * @param memoryLimit 运行时内存上限（字节）；{@code <= 0} 时使用默认值
     * @param stackSize   JS 调用栈上限（字节）；{@code <= 0} 时使用默认值
     */
    public static void setLimits(long timeoutMs, long memoryLimit, long stackSize) {
        sTimeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        sMemoryLimit = memoryLimit > 0 ? memoryLimit : DEFAULT_MEMORY_LIMIT;
        sStackSize = stackSize > 0 ? stackSize : DEFAULT_STACK_SIZE;
    }

    /**
     * 恢复为默认资源限制。
     */
    public static void resetLimits() {
        sTimeoutMs = DEFAULT_TIMEOUT_MS;
        sMemoryLimit = DEFAULT_MEMORY_LIMIT;
        sStackSize = DEFAULT_STACK_SIZE;
    }

    /**
     * @return 当前配置的超时（毫秒）。
     */
    public static long getTimeoutMs() {
        return sTimeoutMs;
    }

    /**
     * @return 当前配置的内存上限（字节）。
     */
    public static long getMemoryLimit() {
        return sMemoryLimit;
    }

    /**
     * @return 当前配置的栈上限（字节）。
     */
    public static long getStackSize() {
        return sStackSize;
    }

    /* ---------------- 宿主回调桥 ---------------- */

    /**
     * 注册全局宿主回调桥（通常只设置一次）。需要在任何脚本执行前完成。
     */
    public static void setHostBridge(HostBridge bridge) {
        sHostBridge = bridge;
    }

    /**
     * 由 C 层 {@code js_host_call} 通过 JNI 静态方法回调。
     * 方法名与签名必须保持不变（R8 需 keep），否则 C 层 GetStaticMethodID 找不到。
     */
    public static String onHostCall(String name, String argsJson) {
        HostBridge bridge = sHostBridge;
        if (bridge == null) {
            return "null";
        }
        try {
            String result = bridge.onHostCall(name, argsJson);
            return result == null ? "null" : result;
        } catch (Throwable t) {
            return "null";
        }
    }

    /* ---------------- native 声明 ---------------- */

    private static native long nativeCreateRuntime(long timeoutMs,
                                                   long memoryLimit,
                                                   long stackSize);

    private static native long nativeCreateContext(long runtimePtr);

    private static native String nativeEvaluate(long contextPtr, String script,
                                                String filename, String varName);

    private static native String nativeEvaluateJson(long contextPtr, String script,
                                                    String filename);

    private static native boolean nativeHasGlobalFunction(long contextPtr, String name);

    private static native boolean nativeHasGlobal(long contextPtr, String name);

    private static native String nativeCallFunction(long contextPtr, String name,
                                                    String argsJson);

    private static native String nativeGetGlobalJson(long contextPtr, String name);

    private static native boolean nativeSetGlobal(long contextPtr, String name,
                                                  String valueJson);

    private static native void nativeFreeContext(long contextPtr);

    private static native void nativeFreeRuntime(long runtimePtr);

    /* ---------------- 脚本执行 ---------------- */

    /**
     * 执行一段 JS 脚本，返回表达式结果转成的字符串。
     * 执行出错（含超时）时返回空字符串。
     *
     * @param script JS 代码
     * @return 结果字符串，出错时为 ""
     */
    public String evaluate(String script) {
        return evaluate(script, null);
    }

    /**
     * 执行一段 JS 脚本，返回指定全局变量的字符串值。
     *
     * @param script  JS 代码
     * @param varName 需要读取的全局变量名，为 null 时返回表达式结果
     * @return 结果字符串，出错时为 ""
     */
    public String evaluate(String script, String varName) {
        checkOpen();
        return nativeEvaluate(contextPtr, script, "<eval>", varName);
    }

    /**
     * 执行一段 JS 脚本，将表达式结果以 JSON 序列化返回
     * （undefined / 异常统一为 {@code "null"}）。可用于读取类型化结果。
     *
     * @param script JS 代码
     * @return JSON 文本，如 {@code "123"}、{@code "[\"a\",1]"}、{@code "null"}
     */
    public String evaluateJson(String script) {
        checkOpen();
        return nativeEvaluateJson(contextPtr, script, "<eval>");
    }

    /* ---------------- 全局对象访问 ---------------- */

    /**
     * 判断全局对象上是否存在指定函数。
     *
     * @param name 全局函数名
     * @return true 表示存在且可调用
     */
    public boolean hasFunction(String name) {
        checkOpen();
        return nativeHasGlobalFunction(contextPtr, name);
    }

    /**
     * 判断全局对象上是否存在指定属性（含值为 undefined 的属性）。
     */
    public boolean hasGlobal(String name) {
        checkOpen();
        return nativeHasGlobal(contextPtr, name);
    }

    /**
     * 调用全局对象上的函数。
     *
     * @param name     全局函数名
     * @param argsJson 参数 JSON 数组文本，如 {@code "[\"a\",1,true]"}；可为 null 表示无参
     * @return 返回值 JSON 文本；函数不存在 / 调用异常 / 不可序列化时为 {@code "null"}
     */
    public String callFunction(String name, String argsJson) {
        checkOpen();
        return nativeCallFunction(contextPtr, name, argsJson);
    }

    /**
     * 调用全局对象上的函数（便捷重载，无参）。
     */
    public String callFunction(String name) {
        return callFunction(name, (String) null);
    }

    /**
     * 读取全局变量的值（JSON 序列化）。
     *
     * @param name 全局变量名
     * @return JSON 文本；变量不存在 / undefined / 异常时为 {@code "null"}
     */
    public String getGlobalJson(String name) {
        checkOpen();
        return nativeGetGlobalJson(contextPtr, name);
    }

    /**
     * 把一个 JSON 值写入全局变量。
     *
     * @param name      全局变量名
     * @param valueJson JSON 文本，如 {@code "\"abc\""}、{@code "123"}、{@code "null"}
     * @return true 表示写入成功；JSON 非法时返回 false
     */
    public boolean setGlobal(String name, String valueJson) {
        checkOpen();
        return nativeSetGlobal(contextPtr, name, valueJson);
    }

    /* ---------------- 生命周期 ---------------- */

    private void checkOpen() {
        if (contextPtr == 0) {
            throw new IllegalStateException("QuickJSEngine already closed");
        }
    }

    /**
     * 判断引擎是否已关闭（底层 runtime/context 已释放）。
     * 用于 ThreadLocal 会话残留检测：reactive 线程漂移时可能残留已关闭的引擎引用。
     */
    public boolean isClosed() {
        return contextPtr == 0;
    }

    /**
     * 释放底层资源。关闭后不能再调用任何方法。
     */
    public void close() {
        if (contextPtr != 0) {
            nativeFreeContext(contextPtr);
            contextPtr = 0;
        }
        if (runtimePtr != 0) {
            nativeFreeRuntime(runtimePtr);
            runtimePtr = 0;
        }
    }

    /* ---------------- 内部接口 ---------------- */

    /**
     * JS→Java 宿主回调桥。由 {@link #setHostBridge(HostBridge)} 注册一个全局处理器，
     * C 层 JS 脚本调用 {@code hostCall(name, argsJson)} 时会经
     * {@link #onHostCall(String, String)} 分发到该处理器。
     */
    public interface HostBridge {
        /**
         * 处理一次宿主调用。
         *
         * @param name     调用名（如 "dom"、"md5"、"aes_cbc"、"state"、"login" 等）
         * @param argsJson 参数 JSON 字符串
         * @return 结果 JSON 字符串；无法处理时返回 {@code "null"}
         */
        String onHostCall(String name, String argsJson);
    }
}