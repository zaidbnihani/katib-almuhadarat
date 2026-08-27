# كاتب المحاضرات — نسخة Android

تطبيق Android أصلي (Kotlin + Jetpack Compose) يحوّل الفيديو/الصوت إلى نص عربي+إنجليزي باستخدام **whisper.cpp** عبر NDK — يعمل **بدون إنترنت** بعد تنزيل النموذج.

## الميزتان
1. **من الفيديو**: اختيار ملف فيديو/صوت من الجهاز → استخراج الصوت بـ MediaExtractor/MediaCodec → تفريغ بـ Whisper.
2. **تسجيل مباشر**: تسجيل بالميكروفون مع موجة حية → إنهاء → تفريغ فوري.

## المتطلبات
- Android Studio Hedgehog أو أحدث
- JDK 17
- NDK 26+ (يُثبّت من SDK Manager → SDK Tools → NDK)
- CMake 3.22+

## التشغيل

1. افتح مجلد `android/` في Android Studio.
2. انتظر مزامنة Gradle (سيحمّل whisper.cpp تلقائيًا عبر FetchContent عند أول بناء).
3. شغّل على جهاز/محاكي (Android 7+، يُفضّل جهاز حقيقي للسرعة).
4. عند أول تشغيل اضغط **«تنزيل النموذج»** (~465MB) — يُحفظ في `getExternalFilesDir()`.

## البناء APK

```bash
# داخل android/
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # للإصدار النهائي (وقّعه)
```

## التفاصيل التقنية
- **JNI**: `app/src/main/cpp/whisper_jni.cpp` يغلّف `whisper.h` (init/transcribe).
- **CMake**: يجلب whisper.cpp tag `b4938` تلقائيًا (FetchContent)، يبني `libwhisper + libggml`.
- **استخراج الصوت**: `MediaExtractor + MediaCodec` (بدون FFmpeg) + resample خطي إلى 16kHz.
- **الأذونات**: `RECORD_AUDIO` + `READ_MEDIA_VIDEO/AUDIO` (+ fallback لـ Android <13).
- **النموذج**: `ggml-small.bin` يدعم العربية والإنجليزية (`language=auto`).

## الأيقونة
مأخوذة من `computer/assets/icon-512.png` (ميكروفون بتدرج أزرق-بنفسجي + حرف ك).

## ملاحظات
- أول تفريغ بطيء نسبيًا (تحميل النموذج للذاكرة).
- على الأجهزة الضعيفة استبدل النموذج بـ `ggml-base.bin` (~148MB) عبر تغيير `MODEL_URL` في `WhisperHelper.kt`.
