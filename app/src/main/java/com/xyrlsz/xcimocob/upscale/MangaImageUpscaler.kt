package com.xyrlsz.xcimocob.upscale

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.scale
import com.xyrlsz.xcimocob.App
import io.github.aoihoshino.realcugan_android_sdk.ModelName
import io.github.aoihoshino.realcugan_android_sdk.RealCUGAN
import io.github.aoihoshino.realcugan_android_sdk.RealCUGANOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 漫画超分的执行入口。
 *
 * 推理本身不能多线程并行：SDK 只有一个 GPU 引擎、一条 Vulkan 队列，多开引擎会成倍占用显存，
 * 部分 Mali 驱动还会在并发 create/release 时崩溃。所以真正省时间的是把「等待」挪出阅读路径：
 * 阅读线程管当前页，后台预取线程提前算好下一页并落盘，翻页时直接命中缓存。
 */
object MangaImageUpscaler {
    private const val TAG = "MangaImageUpscaler"

    /** 固定 2 倍放大：更高倍率的收益很小，推理耗时会成倍增加 */
    private const val SCALE = 2

    /** 参与推理的输入像素上限，超出时先缩小，避免超大图拖慢翻页 */
    private const val MAX_INPUT_PIXELS = 1_600_000L
    private const val MAX_INPUT_SIDE = 1600

    /** 同一张图已有别的线程在推理时，最多等这么久再自己算 */
    private const val IN_FLIGHT_TIMEOUT_SECONDS = 60L

    /** 前台正在超分时，预取最多让位这么久；超时直接放弃，等翻到那页再算 */
    private const val PREFETCH_YIELD_MS = 8000L

    /** 等待同一张图的异步落盘 */
    private const val CACHE_WRITE_WAIT_MS = 1500L

    private val lock = Any()
    private val lifecycleExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "RealCUGAN-lifecycle").apply { isDaemon = true }
    }

    /** 预取线程：解码、缩放、PNG 编码与当前页推理并行，只有推理本身排队 */
    private val prefetchExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "RealCUGAN-prefetch").apply { isDaemon = true }
    }

    private var engine: RealCUGAN? = null

    /** 正在推理的缓存键，避免预取与阅读线程重复算同一张图 */
    private val inFlight = ConcurrentHashMap<String, CountDownLatch>()

    /** 阅读线程正在推理的数量：>0 时预取主动让位，保证当前页优先 */
    private val displayInFlight = AtomicInteger(0)

    /**
     * @param targetWidth 该图在阅读界面上实际占用的宽度（px），0 表示未知
     * @param cacheKey 磁盘缓存标识（通常是图片 URL），为空表示不缓存
     * @return 放大后的位图；原图已经不小于 2 倍屏幕宽度时返回 null，表示无需放大
     */
    @JvmStatic
    fun upscale(bitmap: Bitmap, targetWidth: Int, cacheKey: String?): Bitmap? =
        upscale(bitmap, targetWidth, cacheKey, false)

    /**
     * 后台预取：解码图片字节后按同样的规则超分并直接落盘，翻到这一页时 upscale() 会命中缓存。
     * 解码在预取线程完成，不占用阅读线程。
     *
     * @param bytes 原始图片字节（来自 Fresco 的编码缓存或网络）
     * @param cacheKey 与阅读线程一致的缓存标识
     */
    @JvmStatic
    fun prefetchAsync(bytes: ByteArray, targetWidth: Int, cacheKey: String?) {
        if (targetWidth <= 0 || cacheKey == null) {
            return
        }
        val key = UpscaleDiskCache.key(cacheKey, targetWidth)
        if (UpscaleDiskCache.contains(key) || inFlight.containsKey(key)) {
            return
        }
        prefetchExecutor.execute {
            try {
                if (UpscaleDiskCache.contains(key)) {
                    return@execute
                }
                if (!awaitDisplayIdle()) {
                    return@execute
                }
                val decoded = decodeSampled(bytes) ?: return@execute
                val output = try {
                    upscale(decoded, targetWidth, cacheKey, true)
                } finally {
                    if (!decoded.isRecycled) {
                        decoded.recycle()
                    }
                }
                if (output != null && !output.isRecycled) {
                    output.recycle()
                }
            } catch (error: Throwable) {
                Log.w(TAG, "RealCUGAN prefetch failed", error)
            }
        }
    }

    /**
     * 提前创建推理引擎：首次使用要拷贝模型并初始化 Vulkan，改在阅读器打开时后台完成，
     * 避免第一张图等待数秒。
     */
    @JvmStatic
    fun warmUpAsync() {
        lifecycleExecutor.execute {
            synchronized(lock) {
                try {
                    runBlocking(Dispatchers.IO) { ensureEngine() }
                } catch (exception: Exception) {
                    Log.w(TAG, "RealCUGAN warm up failed", exception)
                } catch (error: LinkageError) {
                    Log.w(TAG, "RealCUGAN warm up failed", error)
                }
            }
        }
    }

    @JvmStatic
    fun releaseAsync() {
        lifecycleExecutor.execute {
            synchronized(lock) {
                val previousEngine = engine
                engine = null
                try {
                    previousEngine?.release()
                } catch (exception: Exception) {
                    Log.e(TAG, "Failed to release RealCUGAN engine", exception)
                } catch (error: LinkageError) {
                    Log.e(TAG, "Failed to release RealCUGAN native engine", error)
                }
            }
        }
    }

    /**
     * @param saveSync 预取时同步写盘，保证等待同一张图的线程醒来就能读到缓存
     */
    private fun upscale(bitmap: Bitmap, targetWidth: Int, cacheKey: String?, saveSync: Boolean): Bitmap? {
        if (targetWidth > 0 && bitmap.width >= targetWidth * SCALE) {
            return null
        }
        if (cacheKey == null) {
            return runUpscale(bitmap, null, false, null)
        }
        val key = UpscaleDiskCache.key(cacheKey, targetWidth)
        UpscaleDiskCache.load(key)?.let { return it }
        val mine = CountDownLatch(1)
        val existing = inFlight.putIfAbsent(key, mine)
        if (existing != null) {
            // 同一张图已经在别的线程推理，等它写完盘直接读结果，避免重复跑一次模型
            awaitQuietly(existing)
            loadWhenReady(key)?.let { return it }
            return runUpscale(bitmap, key, saveSync, null)
        }
        return runUpscale(bitmap, key, saveSync, mine)
    }

    private fun runUpscale(bitmap: Bitmap, key: String?, saveSync: Boolean,
                           latch: CountDownLatch?): Bitmap? {
        try {
            val inputSize = inferInputSize(bitmap.width, bitmap.height)
            val input = if (inputSize[0] == bitmap.width && inputSize[1] == bitmap.height) {
                bitmap
            } else {
                bitmap.scale(inputSize[0], inputSize[1])
            }
            try {
                val imageBytes = encode(input)
                val output = if (saveSync) {
                    synchronized(lock) {
                        runBlocking(Dispatchers.IO) {
                            ensureEngine().process(imageBytes)
                        }
                    }
                } else {
                    displayInFlight.incrementAndGet()
                    try {
                        synchronized(lock) {
                            runBlocking(Dispatchers.IO) {
                                ensureEngine().process(imageBytes)
                            }
                        }
                    } finally {
                        displayInFlight.decrementAndGet()
                    }
                }
                if (key != null) {
                    if (saveSync) {
                        UpscaleDiskCache.save(key, output)
                    } else {
                        UpscaleDiskCache.saveAsync(key, output)
                    }
                }
                return output
            } finally {
                if (input !== bitmap && !input.isRecycled()) {
                    input.recycle()
                }
            }
        } finally {
            if (key != null && latch != null) {
                inFlight.remove(key)
                latch.countDown()
            }
        }
    }

    private fun awaitQuietly(latch: CountDownLatch) {
        try {
            latch.await(IN_FLIGHT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** 别的线程写盘是异步的，等结果真正落盘再读，避免又跑一遍模型 */
    private fun loadWhenReady(key: String): Bitmap? {
        val deadline = SystemClock.elapsedRealtime() + CACHE_WRITE_WAIT_MS
        while (true) {
            UpscaleDiskCache.load(key)?.let { return it }
            if (SystemClock.elapsedRealtime() >= deadline) {
                return null
            }
            try {
                Thread.sleep(50)
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
    }

    /** 前台有推理在排队时先让位；让位超时就放弃这次预取，翻到那页会照常现算 */
    private fun awaitDisplayIdle(): Boolean {
        val deadline = SystemClock.elapsedRealtime() + PREFETCH_YIELD_MS
        while (displayInFlight.get() > 0) {
            if (SystemClock.elapsedRealtime() >= deadline) {
                return false
            }
            try {
                Thread.sleep(50)
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return true
    }

    private suspend fun ensureEngine(): RealCUGAN =
        engine ?: RealCUGAN.create(
            RealCUGANOption(
                context = App.getAppContext(),
                noise = -1,
                scale = SCALE,
                syncgap = 3,
                modelName = ModelName.SE,
                ttaMode = false,
                gpuId = 0
            )
        ).also { engine = it }

    /**
     * 推理耗时大致与像素数成正比，大图先缩到上限内；模型只放大 2 倍，
     * 缩小后的输入仍能覆盖屏幕显示所需的分辨率。
     */
    private fun inferInputSize(width: Int, height: Int): IntArray {
        val pixels = width.toLong() * height
        val maxSide = max(width, height)
        var ratio = 1.0
        if (pixels > MAX_INPUT_PIXELS) {
            ratio = sqrt(MAX_INPUT_PIXELS.toDouble() / pixels)
        }
        if (maxSide * ratio > MAX_INPUT_SIDE) {
            ratio = MAX_INPUT_SIDE.toDouble() / maxSide
        }
        if (ratio >= 1.0) {
            return intArrayOf(width, height)
        }
        return intArrayOf(
            (width * ratio).toInt().coerceAtLeast(1),
            (height * ratio).toInt().coerceAtLeast(1)
        )
    }

    /** 预取时按推理上限采样解码，避免为了超分再解一张全尺寸大图 */
    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        val target = inferInputSize(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= target[0] &&
            bounds.outHeight / (sampleSize * 2) >= target[1]
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (error: Throwable) {
            Log.w(TAG, "Failed to decode image for prefetch", error)
            null
        }
    }

    private fun encode(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream(bitmap.byteCount)
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            "Failed to encode bitmap for RealCUGAN"
        }
        return output.toByteArray()
    }
}
