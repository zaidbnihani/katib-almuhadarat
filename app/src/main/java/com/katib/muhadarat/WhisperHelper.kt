package com.katib.muhadarat

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * نظام تفريغ الصوت والفيديو الذكي لنظام الأندرويد باستخدام Google Gemini AI
 * يدعم استخراج الصوت من الفيديو والتسجيل المباشر والتفريغ فائق الدقة والسرعة
 */
class WhisperHelper(private val context: Context) {

    companion object {
        private const val TAG = "KatibGemini"
        
        // مفتاح API لـ Google Gemini
        private val GEMINI_API_KEY: String
            get() = String(Base64.decode("QVEuQWI4Uk42TDlXMnBEVzBzZUF2RExXWTk0Q1hyNjZEY2hqU1Q3cjQzOHVkSEVBQS1mUVE=", Base64.NO_WRAP))

        private val CANDIDATE_MODELS = listOf(
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.5-transcribe",
            "gemini-3.6-flash",
            "gemini-flash-latest"
        )

        private const val PROMPT = """أنت نظام تفريغ صوتي فائق السرعة والدقة.
استمع لهذا المقطع الصوتي واكتب كل الكلام المنطوق فيه نصياً بدقة تامة 100% كما قيل تماماً دون أي تغيير أو حذف أو زيادة، مع علامات الترقيم.
أعد النص المستخرج فقط مباشرة دون أي مقدمة أو تعليق."""
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun isModelReady(): Boolean = true

    fun freeModel() {
        // لا توجد موارد محلية ثقيلة للتحرير
    }

    /**
     * تفريغ ملف فيديو أو صوت عبر استخراج مسار الصوت وإرساله لـ Gemini
     */
    suspend fun transcribeFile(file: File, mimeType: String = ""): String = withContext(Dispatchers.IO) {
        try {
            // 1. إذا كان الملف صوتياً خفيفاً (أقل من 15 ميجابايت) أرسله مباشرة
            val isPureAudio = file.extension.lowercase() in listOf("mp3", "wav", "m4a", "aac", "ogg", "flac")
            if (isPureAudio && file.length() < 15 * 1024 * 1024) {
                val actualMime = when (file.extension.lowercase()) {
                    "mp3" -> "audio/mp3"
                    "wav" -> "audio/wav"
                    "m4a" -> "audio/mp4"
                    "aac" -> "audio/aac"
                    "ogg" -> "audio/ogg"
                    else -> "audio/mp3"
                }
                val fileBytes = file.readBytes()
                val base64Data = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
                return@withContext callGeminiApi(base64Data, actualMime)
            }

            // 2. إذا كان فيديو أو ملف صوت كبير، استخرج الصوت بـ MediaExtractor و MediaCodec وحوله إلى WAV 16kHz
            val pcmFloats = extractPcm16k(file)
            if (pcmFloats.isEmpty()) {
                throw IllegalStateException("لم يتم العثور على مسار صوتي صالح في الملف")
            }

            val wavFile = File(context.cacheDir, "extracted_${System.currentTimeMillis()}.wav")
            writeWavFile(pcmFloats, wavFile, 16000)

            val wavBytes = wavFile.readBytes()
            try { wavFile.delete() } catch (_: Exception) {}

            val base64Data = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
            return@withContext callGeminiApi(base64Data, "audio/wav")
        } catch (e: Exception) {
            Log.e(TAG, "فشل معالجة وتفريغ الملف", e)
            throw e
        }
    }

    /**
     * استخراج بيانات الصوت PCM 16kHz mono من أي ملف فيديو أو صوت
     */
    private fun extractPcm16k(file: File): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var trackIdx = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIdx = i
                    format = f
                    break
                }
            }

            if (trackIdx < 0 || format == null) {
                throw IllegalArgumentException("لا يوجد مسار صوتي داخل الملف")
            }

            extractor.selectTrack(trackIdx)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val outPcm = mutableListOf<Float>()
            val info = MediaCodec.BufferInfo()
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
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
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
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val shortBuf = outBuf.order(ByteOrder.nativeOrder()).asShortBuffer()
                        while (shortBuf.hasRemaining()) {
                            outPcm.add(shortBuf.get() / 32768f)
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            val srcRate = try { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Exception) { 44100 }
            return if (srcRate != 16000) resample(outPcm.toFloatArray(), srcRate, 16000) else outPcm.toFloatArray()
        } catch (e: Exception) {
            try { extractor.release() } catch (_: Exception) {}
            throw e
        }
    }

    private fun resample(input: FloatArray, src: Int, dst: Int): FloatArray {
        if (src == dst || input.isEmpty()) return input
        val ratio = src.toDouble() / dst
        val outLen = (input.size / ratio).toInt()
        return FloatArray(outLen) { i ->
            val pos = i * ratio
            val idx = pos.toInt()
            val frac = (pos - idx).toFloat()
            if (idx + 1 < input.size) {
                input[idx] * (1 - frac) + input[idx + 1] * frac
            } else {
                input.getOrElse(idx) { 0f }
            }
        }
    }

    /**
     * حفظ عينات الصوت PCM إلى ملف WAV سليم
     */
    private fun writeWavFile(pcmFloats: FloatArray, destFile: File, sampleRate: Int = 16000) {
        val totalAudioLen = pcmFloats.size * 2
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = sampleRate * 2 * channels

        val header = ByteArray(44)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt(totalDataLen)
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16) // Subchunk1Size
        bb.putShort(1) // AudioFormat (1 = PCM)
        bb.putShort(channels.toShort())
        bb.putInt(sampleRate)
        bb.putInt(byteRate)
        bb.putShort(2) // BlockAlign
        bb.putShort(16) // BitsPerSample
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(totalAudioLen)

        destFile.outputStream().use { fos ->
            fos.write(header)
            val pcmBytes = ByteArray(pcmFloats.size * 2)
            val pcmBb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (f in pcmFloats) {
                val s = (f.coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
                pcmBb.putShort(s)
            }
            fos.write(pcmBytes)
            fos.flush()
        }
    }

    /**
     * استدعاء Google Gemini API
     */
    private suspend fun callGeminiApi(base64Audio: String, mimeType: String): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null

        for (model in CANDIDATE_MODELS) {
            try {
                val jsonPayload = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val partsArray = JSONArray().apply {
                                val inlineDataObj = JSONObject().apply {
                                    put("mimeType", mimeType)
                                    put("data", base64Audio)
                                }
                                val part1 = JSONObject().apply {
                                    put("inlineData", inlineDataObj)
                                }
                                put(part1)

                                val part2 = JSONObject().apply {
                                    put("text", PROMPT)
                                }
                                put(part2)
                            }
                            put("parts", partsArray)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArray)

                    val generationConfig = JSONObject().apply {
                        put("temperature", 0.1)
                        put("maxOutputTokens", 8192)
                    }
                    put("generationConfig", generationConfig)
                }

                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$GEMINI_API_KEY"
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonPayload.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    Log.w(TAG, "فشل مع النموذج $model (كود ${response.code}): $responseBody")
                    lastError = Exception("خطأ ${response.code}: $responseBody")
                    continue
                }

                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val sb = StringBuilder()
                        for (i in 0 until parts.length()) {
                            val partObj = parts.getJSONObject(i)
                            val text = partObj.optString("text", "")
                            if (text.isNotBlank()) {
                                if (sb.isNotEmpty()) sb.append("\n")
                                sb.append(text)
                            }
                        }
                        val result = sb.toString().trim()
                        if (result.isNotEmpty()) {
                            return@withContext result
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "خطأ أثناء محاولة $model", e)
                lastError = e
            }
        }

        throw lastError ?: Exception("تعذر استخراج النص من المقطع الصوتي")
    }
}
