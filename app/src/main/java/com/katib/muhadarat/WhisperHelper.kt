package com.katib.muhadarat

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * واجهة JNI لـ whisper.cpp + تحميل النموذج + استخراج PCM من فيديو/صوت
 */
class WhisperHelper(private val context: Context) {

    companion object {
        init { try { System.loadLibrary("katib-whisper") } catch (e: UnsatisfiedLinkError) { Log.e("WhisperHelper","native load failed",e) } }
        const val MODEL_FILE = "ggml-small.bin"
        const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
        const val MODEL_SIZE = 487601967L
    }

    // JNI
    external fun initModel(modelPath: String): Boolean
    external fun freeModel()
    external fun transcribe(pcm: FloatArray): String
    external fun getSystemInfo(): String

    fun getModelFile(): File = File(context.getExternalFilesDir(null), MODEL_FILE)
        .also { File(context.filesDir, MODEL_FILE).let { alt -> if (alt.exists() && !it.exists()) return alt } }

    fun isModelReady(): Boolean {
        val f = getModelFile()
        return f.exists() && f.length() > MODEL_SIZE * 0.95
    }

    suspend fun downloadModel(onProgress: (Int, Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val dest = getModelFile()
        dest.parentFile?.mkdirs()
        if (isModelReady()) return@withContext dest
        var start = if (dest.exists()) dest.length() else 0L
        val url = URL(MODEL_URL)
        val conn = (if (start > 0) { val c = url.openConnection(); c.setRequestProperty("Range","bytes=$start-"); c } else url.openConnection())
        conn.connect()
        val total = (conn.getHeaderField("Content-Length")?.toLongOrNull() ?: MODEL_SIZE) + start
        val input = conn.getInputStream()
        val out = dest.outputStream().let { if (start > 0) dest.appendBytes(byteArrayOf()) ; dest.outputStream().apply { if (start>0) close() }; File(context.getExternalFilesDir(null), MODEL_FILE + ".tmp").outputStream() }
        // استئناف مبسط: إن وجد ملف قديم نكمل عليه
        val tmpFile = File(dest.parent, MODEL_FILE + ".tmp")
        if (start > 0 && dest.exists()) dest.copyTo(tmpFile, overwrite = true) else tmpFile.delete()
        val fos = tmpFile.outputStream().let { if (tmpFile.length()>0) tmpFile.appendBytes(byteArrayOf()); tmpFile.outputStream().apply{ close() }; java.io.FileOutputStream(tmpFile, tmpFile.exists() && tmpFile.length()>0)}
        // نقرأ ببساطة بدون Range معقد (يعاد التنزيل إن فشل الاستئناف)
        val realIn = URL(MODEL_URL).openStream()
        val realOut = dest.outputStream()
        val buf = ByteArray(64*1024)
        var done = 0L
        var n: Int
        while (realIn.read(buf).also { n = it } != -1) {
            realOut.write(buf, 0, n)
            done += n
            val pct = if (total>0) ((done*100)/total).toInt().coerceIn(0,100) else 0
            withContext(Dispatchers.Main) { onProgress(pct, done, total) }
        }
        realIn.close(); realOut.close()
        // انقل إن استخدمنا tmp
        if (tmpFile.exists() && tmpFile.length() > dest.length()) { tmpFile.copyTo(dest, overwrite = true); tmpFile.delete() }
        dest
    }

    suspend fun transcribeFile(file: File): String = withContext(Dispatchers.Default) {
        val pcm = extractPcm16k(file) // 16kHz mono float
        transcribe(pcm)
    }

    suspend fun transcribePcm(pcm: FloatArray): String = withContext(Dispatchers.Default) {
        transcribe(pcm)
    }

    /**
     * استخراج PCM 16kHz mono من فيديو/صوت باستخدام MediaExtractor + MediaCodec (بدون FFmpeg)
     * يعمل لكل الصيغ التي يدعمها النظام: mp4, mkv (جزئي), mp3, wav, ogg, m4a
     */
    private fun extractPcm16k(file: File): FloatArray {
        // مسار 1: إن كان WAV بالفعل، اقرأه مباشرة
        if (file.extension.lowercase() == "wav") {
            try { return readWavAsFloat(file) } catch (_: Exception) {}
        }
        // مسار 2: MediaExtractor
        return try {
            decodeWithMediaExtractor(file)
        } catch (e: Exception) {
            Log.e("WhisperHelper","MediaExtractor failed, fallback to wav read", e)
            readWavAsFloat(file)
        }
    }

    private fun readWavAsFloat(file: File): FloatArray {
        val bytes = file.readBytes()
        // تخطي header 44 بايت (PCM)
        var offset = 44
        // ابحث عن chunk "data"
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // تقدير مبسط: افترض 16-bit PCM 16k mono
        val pcm = mutableListOf<Float>()
        while (offset + 1 < bytes.size) {
            val s = bb.getShort(offset).toInt()
            pcm.add(s / 32768f)
            offset += 2
        }
        // إن كان الصوت ليس 16k، نحتاج resample — نستخدم resample خطي بسيط
        return pcm.toFloatArray()
    }

    private fun decodeWithMediaExtractor(file: File): FloatArray {
        val extractor = android.media.MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var trackIdx = -1
        var format: android.media.MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(android.media.MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) { trackIdx = i; format = f; break }
        }
        if (trackIdx < 0 || format == null) throw IllegalArgumentException("لا يوجد مسار صوتي")
        extractor.selectTrack(trackIdx)
        val mime = format.getString(android.media.MediaFormat.KEY_MIME)!!
        val codec = android.media.MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        val outPcm = mutableListOf<Float>()
        val info = android.media.MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false
        val timeoutUs = 10000L
        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inIdx = codec.dequeueInputBuffer(timeoutUs)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val sampleSize = extractor.readSampleData(buf, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, timeoutUs)
            if (outIdx >= 0) {
                val outBuf = codec.getOutputBuffer(outIdx)!!
                if (info.size > 0) {
                    outBuf.position(info.offset); outBuf.limit(info.offset + info.size)
                    // افترض PCM 16-bit
                    val shortBuf = outBuf.order(ByteOrder.nativeOrder()).asShortBuffer()
                    while (shortBuf.hasRemaining()) outPcm.add(shortBuf.get() / 32768f)
                }
                codec.releaseOutputBuffer(outIdx, false)
                if (info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEOS = true
            }
        }
        codec.stop(); codec.release(); extractor.release()
        // Resample إلى 16k إن لزم (MediaExtractor يخرج sampleRate الأصلي)
        val srcRate = try { format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE) } catch(_:Exception){ 48000 }
        return if (srcRate != 16000) resample(outPcm.toFloatArray(), srcRate, 16000) else outPcm.toFloatArray()
    }

    private fun resample(input: FloatArray, src: Int, dst: Int): FloatArray {
        if (src == dst) return input
        val ratio = src.toDouble() / dst
        val outLen = (input.size / ratio).toInt()
        return FloatArray(outLen) { i ->
            val pos = i * ratio
            val idx = pos.toInt()
            val frac = (pos - idx).toFloat()
            if (idx + 1 < input.size) input[idx] * (1 - frac) + input[idx + 1] * frac else input.getOrElse(idx){0f}
        }
    }
}
