package com.katib.muhadarat

import android.content.Context
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
import java.util.concurrent.TimeUnit

/**
 * نظام تفريغ الصوت والفيديو الذكي باستخدام Google Gemini AI
 * نفس النموذج السحابي فائق السرعة والدقة المستخدم في تطبيق الحاسوب
 */
class WhisperHelper(private val context: Context) {

    companion object {
        private const val TAG = "KatibGemini"
        private val GEMINI_API_KEY: String
            get() = String(Base64.decode("QVEuQWI4Uk42TDlXMnBEVzBzZUF2RExXWTk0Q1hyNjZEY2hqU1Q3cjQzOHVkSEVBQS1mUVE=", Base64.NO_WRAP))

        private val CANDIDATE_MODELS = listOf(
            "gemini-2.5-flash-lite",
            "gemini-flash-lite-latest",
            "gemini-2.5-flash",
            "gemini-2.0-flash"
        )

        private const val PROMPT = """أنت نظام تفريغ صوتي فائق السرعة والدقة.
استمع لهذا المقطع واكتب كل الكلام المنطوق فيه نصياً بدقة تامة 100% كما قيل تماماً دون أي تغيير أو حذف أو زيادة، مع علامات الترقيم.
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

    suspend fun transcribeFile(file: File, mimeType: String = ""): String = withContext(Dispatchers.IO) {
        val actualMime = when {
            mimeType.isNotBlank() -> mimeType
            file.extension.equals("mp3", true) -> "audio/mp3"
            file.extension.equals("m4a", true) -> "audio/mp4"
            file.extension.equals("wav", true) -> "audio/wav"
            file.extension.equals("aac", true) -> "audio/aac"
            file.extension.equals("ogg", true) -> "audio/ogg"
            file.extension.equals("mp4", true) -> "video/mp4"
            file.extension.equals("mov", true) -> "video/quicktime"
            file.extension.equals("mkv", true) -> "video/x-matroska"
            file.extension.equals("webm", true) -> "video/webm"
            else -> "audio/mp3"
        }

        val fileBytes = file.readBytes()
        val base64Data = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
        callGeminiApi(base64Data, actualMime)
    }

    private suspend fun callGeminiApi(base64Audio: String, mimeType: String): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null

        for (model in CANDIDATE_MODELS) {
            try {
                val jsonPayload = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val partsArray = JSONArray().apply {
                                // الجزء الأول: البيانات الصوتية
                                val inlineDataObj = JSONObject().apply {
                                    put("mimeType", mimeType)
                                    put("data", base64Audio)
                                }
                                val part1 = JSONObject().apply {
                                    put("inlineData", inlineDataObj)
                                }
                                put(part1)

                                // الجزء الثاني: موجه الاستخراج
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
