package com.xyrlsz.xcimocob.upscale

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.xyrlsz.xcimocob.App
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * 超分结果的磁盘缓存：命中后直接读盘，跳过推理。
 * 文件放在 cacheDir 下（可被系统随时清理），按「缓存版本 + 目标宽度 + 源图」生成文件名。
 *
 * 键里只放目标显示宽度而不放输入像素尺寸：阅读线程的输入是 Fresco 按屏幕尺寸缩放过的，
 * 后台预取用的是原图，两者的输入尺寸不同；只有键一致，预取的结果才能被阅读线程直接命中。
 */
object UpscaleDiskCache {
    private const val TAG = "UpscaleDiskCache"
    private const val DIR_NAME = "upscale"
    private const val MAX_BYTES = 256L * 1024 * 1024

    /** 模型参数、键规则或输出格式变化时递增，让旧缓存自然失效 */
    private const val CACHE_VERSION = 2

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "UpscaleDiskCache").apply { isDaemon = true }
    }

    @JvmStatic
    fun key(source: String, targetWidth: Int): String =
        "v$CACHE_VERSION|$targetWidth|$source"

    @JvmStatic
    fun contains(key: String): Boolean = fileFor(key).isFile

    @JvmStatic
    fun load(key: String): Bitmap? {
        val file = fileFor(key)
        if (!file.isFile) {
            return null
        }
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options())
            if (bitmap == null) {
                file.delete()
                null
            } else {
                file.setLastModified(System.currentTimeMillis())
                bitmap
            }
        } catch (error: Throwable) {
            file.delete()
            Log.w(TAG, "Failed to read upscale cache", error)
            null
        }
    }

    /** 同步落盘：后台预取在做完之后立刻写盘，好让随后的阅读请求直接命中 */
    @JvmStatic
    fun save(key: String, bitmap: Bitmap) {
        write(key, bitmap)
    }

    @JvmStatic
    fun saveAsync(key: String, bitmap: Bitmap) {
        // 位图用完会被回收，先复制一份再交给后台线程编码写盘
        val snapshot = try {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } catch (error: Throwable) {
            Log.w(TAG, "Failed to copy bitmap for upscale cache", error)
            null
        } ?: return
        executor.execute {
            try {
                write(key, snapshot)
            } finally {
                snapshot.recycle()
            }
        }
    }

    @JvmStatic
    fun clearAsync() {
        executor.execute {
            cacheDir().listFiles()?.forEach { it.delete() }
        }
    }

    private fun write(key: String, bitmap: Bitmap) {
        val dir = cacheDir()
        val target = fileFor(key)
        val temp = File(dir, target.name + ".tmp")
        try {
            FileOutputStream(temp).use { output ->
                // PNG 无损：JMTT 这类按像素通道取值的图片重读后仍能正确解码
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IllegalStateException("compress failed")
                }
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                return
            }
            target.setLastModified(System.currentTimeMillis())
        } catch (error: Throwable) {
            temp.delete()
            Log.w(TAG, "Failed to write upscale cache", error)
            return
        }
        trim(dir)
    }

    private fun trim(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) {
            return
        }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_BYTES) {
                return
            }
            val length = file.length()
            if (file.delete()) {
                total -= length
            }
        }
    }

    private fun cacheDir(): File =
        File(App.getAppContext().cacheDir, DIR_NAME).apply { mkdirs() }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val name = buildString(digest.size * 2) {
            for (byte in digest) {
                append("%02x".format(byte))
            }
        }
        return File(cacheDir(), name)
    }
}
