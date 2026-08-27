package com.katib.muhadarat.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.katib.muhadarat.R
import com.katib.muhadarat.WhisperHelper
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sin

@Composable
fun TranscribeScreen(helper: WhisperHelper) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) } // 0 من الفيديو، 1 تسجيل مباشر

    // حالة الفيديو / الملف
    var pickedName by remember { mutableStateOf<String?>(null) }
    var isTranscribing by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }

    // النص العام المشترك للنتيجة
    var resultText by remember { mutableStateOf("") }

    // حالة التسجيل المباشر
    var isRecording by remember { mutableStateOf(false) }
    var recFile by remember { mutableStateOf<File?>(null) }
    var recorder: MediaRecorder? by remember { mutableStateOf(null) }

    // حوار الإعدادات لمفتاح API
    var showSettingsDialog by remember { mutableStateOf(false) }

    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        pickedName = uri.lastPathSegment ?: "ملف محدد"
        scope.launch {
            isTranscribing = true
            progressText = "جاري رفع ومعالجة الملف بالذكاء الاصطناعي…"
            try {
                val tmpFile = copyUriToTemp(ctx, uri)
                val mimeType = ctx.contentResolver.getType(uri) ?: ""
                val transcribed = helper.transcribeFile(tmpFile, mimeType)
                resultText = transcribed
                progressText = "تم التفريغ بنجاح ✓"
            } catch (e: Exception) {
                progressText = "حدث خطأ: ${e.localizedMessage ?: e.message}"
                Toast.makeText(ctx, "${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isTranscribing = false
            }
        }
    }

    val permRecordLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(ctx, "إذن الميكروفون مطلوب للتسجيل المباشر", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1221))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Header (أيقونة التطبيق + العنوان + زر الإعدادات) ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = "أيقونة كاتب المحاضرات",
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "كاتب المحاضرات",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            IconButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier
                    .background(Color(0xFF171A33), RoundedCornerShape(10.dp))
                    .size(38.dp)
            ) {
                Text("⚙️", fontSize = 16.sp)
            }
        }

        // ── Tabs (بدون إيموجي - نصوص بالمنتصف) ──
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171A33)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val tabs = listOf("من الفيديو", "تسجيل مباشر")
                tabs.forEachIndexed { i, title ->
                    val isSelected = tab == i
                    Button(
                        onClick = { tab = i },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0xFF6C7CFF) else Color.Transparent,
                            contentColor = if (isSelected) Color.White else Color(0xFF9AA0C3)
                        )
                    ) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // ── محتوى التبويب ──
        if (tab == 0) {
            // قسم اختيار الملف / الفيديو
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1D2142)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { pickFileLauncher.launch("*/*") },
                        enabled = !isTranscribing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C7CFF)),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(50.dp)
                    ) {
                        Text(
                            text = "اختيار ملف",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    pickedName?.let {
                        Text(
                            text = it,
                            color = Color(0xFF9AA0C3),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (isTranscribing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF6C7CFF),
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = progressText,
                                color = Color(0xFFC7D2FE),
                                fontSize = 13.sp
                            )
                        }
                    } else if (progressText.isNotEmpty()) {
                        Text(
                            text = progressText,
                            color = Color(0xFF9AA0C3),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            // قسم التسجيل المباشر
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1D2142)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // موجة صوتية بسيطة
                    WaveCanvas(isRecording)

                    // الزران يظهران دائماً معاً في كل الأوقات
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                permRecordLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                try {
                                    val f = File(ctx.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
                                    val r = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
                                    r.setAudioSource(MediaRecorder.AudioSource.MIC)
                                    r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                    r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                    r.setAudioSamplingRate(16000)
                                    r.setAudioEncodingBitRate(32000)
                                    r.setOutputFile(f.absolutePath)
                                    r.prepare()
                                    r.start()
                                    recorder = r
                                    recFile = f
                                    isRecording = true
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "فشل بدء التسجيل: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = !isRecording && !isTranscribing,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) Color(0xFF065F46) else Color(0xFF10B981),
                                disabledContainerColor = if (isRecording) Color(0xFF065F46) else Color.White.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = if (isRecording) "جارٍ التسجيل…" else "بدء التسجيل",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = {
                                try {
                                    recorder?.stop()
                                    recorder?.release()
                                } catch (_: Exception) {}
                                recorder = null
                                isRecording = false

                                val f = recFile ?: return@Button
                                scope.launch {
                                    isTranscribing = true
                                    progressText = "جاري تفريغ التسجيل بالذكاء الاصطناعي…"
                                    try {
                                        kotlinx.coroutines.delay(150)
                                        val transcribed = helper.transcribeFile(f, "audio/mp4")
                                        resultText = transcribed
                                        progressText = "تم التفريغ بنجاح ✓"
                                    } catch (e: Exception) {
                                        progressText = "خطأ: ${e.localizedMessage ?: e.message}"
                                        Toast.makeText(ctx, "${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isTranscribing = false
                                    }
                                }
                            },
                            enabled = isRecording && !isTranscribing,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444),
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                disabledContentColor = Color(0xFF6B7280)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = "إنهاء وتفريغ",
                                fontWeight = FontWeight.Bold,
                                color = if (isRecording) Color.White else Color(0xFF9AA0C3)
                            )
                        }
                    }

                    if (isTranscribing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF6C7CFF),
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = progressText,
                                color = Color(0xFFC7D2FE),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // ── بطاقة النتيجة مع زر (حفظ) فقط ──
        ResultCard(
            text = resultText,
            onUpdate = { resultText = it }
        )
    }

    // ── حوار إعداد مفتاح API ──
    if (showSettingsDialog) {
        var keyInput by remember { mutableStateOf(if (helper.isUsingCustomKey()) helper.getApiKey() else "") }
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = Color(0xFF171A33),
            title = {
                Text(
                    text = "إعدادات مفتاح Google Gemini",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "التطبيق يأتي مع مفتاح مدمج مسبقاً. إذا واجهت خطأ 403 في منطقتك الجغرافية، يمكنك تشغيل VPN أو وضع مفتاحك الخاص من (aistudio.google.com):",
                        color = Color(0xFF9AA0C3),
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        placeholder = { Text("الصق مفتاح API هنا", color = Color(0xFF7A80A8)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6C7CFF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (keyInput.isNotBlank()) {
                            helper.saveApiKey(keyInput)
                            Toast.makeText(ctx, "تم حفظ المفتاح الجديد بنجاح", Toast.LENGTH_SHORT).show()
                        }
                        showSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C7CFF))
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        helper.resetApiKey()
                        keyInput = ""
                        Toast.makeText(ctx, "تمت استعادة المفتاح الافتراضي", Toast.LENGTH_SHORT).show()
                        showSettingsDialog = false
                    }
                ) {
                    Text("المفتاح الافتراضي", color = Color(0xFF9AA0C3))
                }
            }
        )
    }
}

@Composable
private fun ResultCard(
    text: String,
    onUpdate: (String) -> Unit
) {
    val ctx = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171A33)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "النص المستخرج",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                // زر حفظ فقط
                Button(
                    onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("katib", text))
                        Toast.makeText(ctx, "تم نسخ النص للحافظة", Toast.LENGTH_SHORT).show()
                        try {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                            ctx.startActivity(Intent.createChooser(sendIntent, "حفظ ومشاركة النص"))
                        } catch (_: Exception) {}
                    },
                    enabled = text.isNotEmpty(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C7CFF)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "حفظ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = onUpdate,
                placeholder = {
                    Text("النص سيظهر هنا بعد التفريغ…", color = Color(0xFF7A80A8))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6C7CFF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF6C7CFF)
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun WaveCanvas(active: Boolean) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        while (active) {
            tick++
            kotlinx.coroutines.delay(80)
        }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
    ) {
        val w = size.width
        val h = size.height
        if (!active) {
            drawLine(
                Color.White.copy(alpha = 0.08f),
                Offset(0f, h / 2),
                Offset(w, h / 2),
                strokeWidth = 1.5f
            )
            return@Canvas
        }
        val amp = 20f
        for (x in 0 until w.toInt() step 3) {
            val y = h / 2 +
                    sin((x * 0.08f + tick * 0.6f).toDouble()).toFloat() * amp +
                    sin((x * 0.04f - tick * 0.3f).toDouble()).toFloat() * amp * 0.4f
            drawLine(
                Color(0xFF8EA0FF),
                Offset(x.toFloat(), h / 2),
                Offset(x.toFloat(), y),
                strokeWidth = 2.2f
            )
        }
    }
}

private fun copyUriToTemp(ctx: Context, uri: Uri): File {
    val input = ctx.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("تعذر فتح الملف")
    val mime = ctx.contentResolver.getType(uri) ?: ""
    val ext = when {
        mime.contains("mp4") -> "mp4"
        mime.contains("mp3") -> "mp3"
        mime.contains("wav") -> "wav"
        mime.contains("m4a") -> "m4a"
        mime.contains("quicktime") || mime.contains("mov") -> "mov"
        mime.contains("matroska") || mime.contains("mkv") -> "mkv"
        else -> "tmp"
    }
    val tmp = File(ctx.cacheDir, "katib_input_${System.currentTimeMillis()}.$ext")
    tmp.outputStream().use { out -> input.copyTo(out) }
    input.close()
    return tmp
}
