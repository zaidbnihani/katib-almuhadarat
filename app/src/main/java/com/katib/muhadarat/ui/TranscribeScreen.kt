package com.katib.muhadarat.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.katib.muhadarat.WhisperHelper
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sin

@Composable
fun TranscribeScreen(helper: WhisperHelper) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) } // 0 فيديو، 1 تسجيل
    var modelReady by remember { mutableStateOf(helper.isModelReady()) }
    var dlPct by remember { mutableIntStateOf(0) }
    var dlText by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }

    // فيديو
    var pickedName by remember { mutableStateOf<String?>(null) }
    var videoText by remember { mutableStateOf("") }
    var isTranscribing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }

    // تسجيل
    var isRecording by remember { mutableStateOf(false) }
    var recText by remember { mutableStateOf("") }
    var recFile by remember { mutableStateOf<File?>(null) }
    var recorder: MediaRecorder? by remember { mutableStateOf(null) }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        pickedName = uri.lastPathSegment ?: "ملف"
        scope.launch {
            if (!helper.isModelReady()) { Toast.makeText(ctx,"حمّل النموذج أولاً",Toast.LENGTH_SHORT).show(); return@launch }
            isTranscribing = true; progress = "استخراج الصوت…"
            try {
                val tmp = copyUriToTemp(ctx, uri)
                progress = "تفريغ (قد يستغرق دقيقة)…"
                if (!helper.initModel(helper.getModelFile().absolutePath)) throw IllegalStateException("فشل تحميل النموذج")
                videoText = helper.transcribeFile(tmp)
                progress = "اكتمل"
            } catch (e: Exception) { progress = "خطأ: ${e.message}"; Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show() }
            isTranscribing = false
        }
    }

    val permRec = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(ctx,"إذن الميكروفون مطلوب",Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F1221)).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.size(46.dp).background(Brush.linearGradient(listOf(Color(0xFF6C7CFF), Color(0xFF8B5CF6))), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text("ك", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column { Text("كاتب المحاضرات", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("فيديو → نص  •  تسجيل → نص  •  يعمل بدون إنترنت", color = Color(0xFF9AA0C3), fontSize = 12.sp) }
        }

        // Model card
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF171A33)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (modelReady) "النموذج جاهز ✓" else "النموذج غير موجود — حوالي 465MB", color = if (modelReady) Color(0xFFBBF7D0) else Color(0xFFFDE68A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (!modelReady) {
                    if (isDownloading) {
                        LinearProgressIndicator(progress = { dlPct/100f }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF6C7CFF), trackColor = Color.White.copy(alpha=0.1f))
                        Text("$dlPct%  $dlText", color = Color(0xFF9AA0C3), fontSize = 11.sp)
                    } else {
                        Button(onClick = {
                            isDownloading = true; scope.launch {
                                try { helper.downloadModel { pct, done, total -> dlPct = pct; dlText = "${done/1024/1024}MB / ${total/1024/1024}MB" }; modelReady = true; Toast.makeText(ctx,"اكتمل التنزيل",Toast.LENGTH_SHORT).show() }
                                catch(e:Exception){ Toast.makeText(ctx,"فشل: ${e.message}",Toast.LENGTH_LONG).show() }
                                isDownloading = false
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C7CFF))) { Text("تنزيل النموذج") }
                    }
                }
            }
        }

        // Tabs
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Color.White, indicator = {}, divider = {}) {
            listOf("🎬  من الفيديو","🎙️  تسجيل مباشر").forEachIndexed { i, t ->
                val sel = tab==i
                FilterChip(selected = sel, onClick = { tab = i }, label = { Text(t, color = if(sel) Color.White else Color(0xFF9AA0C3), fontWeight = FontWeight.Bold) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF6C7CFF), containerColor = Color.White.copy(alpha=0.06f)), modifier = Modifier.padding(4.dp))
            }
        }

        if (tab==0) {
            // فيديو
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1D2142)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("اسحب أو اختر فيديو / صوت", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("MP4, MKV, MOV, MP3, WAV, M4A…", color = Color(0xFF9AA0C3), fontSize = 12.sp)
                    Button(onClick = { pickVideo.launch("*/*") }, enabled = !isTranscribing, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C7CFF))) { Text("اختر ملف") }
                    pickedName?.let { Text(it, color = Color(0xFF9AA0C3), fontSize = 11.sp) }
                    if (isTranscribing) { CircularProgressIndicator(color = Color(0xFF6C7CFF), modifier = Modifier.size(22.dp)); Text(progress, color = Color(0xFF9AA0C3), fontSize = 12.sp) }
                    if (progress.isNotEmpty() && !isTranscribing) Text(progress, color = Color(0xFF9AA0C3), fontSize = 12.sp)
                }
            }
            ResultCard(text = videoText, onUpdate = { videoText = it })
        } else {
            // تسجيل
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1D2142)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("سجّل صوتك الآن", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("تحدث بوضوح ثم اضغط إنهاء ليُحوّل فورًا", color = Color(0xFF9AA0C3), fontSize = 12.sp)
                    // موجة بسيطة متحركة
                    WaveCanvas(isRecording)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            permRec.launch(Manifest.permission.RECORD_AUDIO)
                            if (!helper.isModelReady()) { Toast.makeText(ctx,"حمّل النموذج أولاً",Toast.LENGTH_SHORT).show(); return@Button }
                            try {
                                val f = File(ctx.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
                                val r = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
                                r.setAudioSource(MediaRecorder.AudioSource.MIC); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); r.setAudioSamplingRate(16000); r.setAudioEncodingBitRate(32000)
                                r.setOutputFile(f.absolutePath); r.prepare(); r.start()
                                recorder = r; recFile = f; isRecording = true
                            } catch(e:Exception){ Toast.makeText(ctx,"فشل التسجيل: ${e.message}",Toast.LENGTH_LONG).show() }
                        }, enabled = !isRecording, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C7CFF))) { Text("● بدء") }
                        Button(onClick = {
                            try { recorder?.stop(); recorder?.release() } catch(_:Exception){}
                            recorder = null; isRecording = false
                            val f = recFile ?: return@Button
                            scope.launch {
                                try {
                                    if (!helper.initModel(helper.getModelFile().absolutePath)) throw IllegalStateException("النموذج غير جاهز")
                                    recText = "جاري التفريغ…"
                                    recText = helper.transcribeFile(f)
                                } catch(e:Exception){ recText = "خطأ: ${e.message}" }
                            }
                        }, enabled = isRecording, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) { Text("■ إنهاء") }
                        Text(if(isRecording) "يسجّل…" else "جاهز", color = if(isRecording) Color(0xFFF87171) else Color(0xFF9AA0C3), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            ResultCard(text = recText, onUpdate = { recText = it })
        }
    }
}

@Composable
private fun ResultCard(text: String, onUpdate: (String)->Unit) {
    val ctx = LocalContext.current
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF171A33)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("النص", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.setPrimaryClip(ClipData.newPlainText("katib", text)); Toast.makeText(ctx,"تم النسخ",Toast.LENGTH_SHORT).show() }, enabled = text.isNotEmpty()) { Text("نسخ", fontSize = 12.sp) }
                    OutlinedButton(onClick = { onUpdate("") }) { Text("مسح", fontSize = 12.sp) }
                }
            }
            OutlinedTextField(value = text, onValueChange = onUpdate, placeholder = { Text("النص سيظهر هنا…", color = Color(0xFF7A80A8)) }, modifier = Modifier.fillMaxWidth().heightIn(min=140.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6C7CFF), unfocusedBorderColor = Color.White.copy(alpha=0.1f), focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color(0xFF6C7CFF)), shape = RoundedCornerShape(12.dp))
        }
    }
}

@Composable
private fun WaveCanvas(active: Boolean) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) { while(active){ tick++; kotlinx.coroutines.delay(80) } }
    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp).background(Color.Black.copy(alpha=0.25f), RoundedCornerShape(12.dp))) {
        val w = size.width; val h = size.height
        if (!active) { drawLine(Color.White.copy(alpha=0.08f), Offset(0f,h/2), Offset(w,h/2), strokeWidth = 1.5f); return@Canvas }
        val amp = 22f
        for (x in 0 until w.toInt() step 3) {
            val y = h/2 + sin((x*0.08f + tick*0.6f).toDouble()).toFloat()*amp + sin((x*0.04f - tick*0.3f).toDouble()).toFloat()*amp*0.4f
            drawLine(Color(0xFF8EA0FF), Offset(x.toFloat(), h/2), Offset(x.toFloat(), y), strokeWidth = 2.2f)
        }
    }
}

private fun copyUriToTemp(ctx: Context, uri: Uri): File {
    val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("تعذر فتح الملف")
    val ext = ctx.contentResolver.getType(uri)?.substringAfter('/') ?: "mp4"
    val tmp = File(ctx.cacheDir, "pick_${System.currentTimeMillis()}.$ext")
    tmp.outputStream().use { out -> input.copyTo(out) }
    input.close()
    return tmp
}
