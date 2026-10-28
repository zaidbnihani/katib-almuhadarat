# PROJECT_PROFILE.md

## العربية

### الغرض والنطاق
المستودع تطبيق Android أصلي باسم «كاتب المحاضرات» لاختيار ملف صوت/فيديو أو تسجيل صوت مباشر ثم إظهار تفريغ نصي، مع خيارات للتعامل مع النص وتصديره إلى PDF. يستند هذا الوصف إلى `README.md` وواجهة التطبيق `app/src/main/java/com/katib/muhadarat/ui/TranscribeScreen.kt` ومساعد التفريغ `app/src/main/java/com/katib/muhadarat/WhisperHelper.kt` ومصدّر PDF `app/src/main/java/com/katib/muhadarat/PdfExporter.kt`.

**تنبيه حول اختلاف التوثيق والتنفيذ:** يصف `README.md` معالجة Whisper محلية دون اتصال بعد تنزيل نموذج، لكن مسار التنفيذ الحالي في `WhisperHelper.kt` يكوّن طلبات HTTP إلى واجهة Google Gemini ويرسل بيانات الصوت إليها، ويتضمن قائمة بأسماء النماذج ومحاولات بديلة؛ لذلك لا تؤيد الشفرة الحالية وصف العمل دون إنترنت. كذلك يذكر README فتح `android/`، بينما إعدادات Gradle ومجلد `app/` موجودة في جذر المستودع ولا يظهر مجلد `android/` في الشجرة. يوجد تكامل أصلي لـ whisper.cpp في `app/src/main/cpp/`، لكن `WhisperHelper.kt` الحالي لا يستدعي واجهات JNI المبينة في `whisper_jni.cpp`، كما أن `isModelReady()` يعيد قيمة ثابتة. ينبغي حسم هذه الفروقات قبل اعتماد README كدليل تشغيل.

### المكوّنات والتدفق الأساسي
- `MainActivity.kt` ينشئ `WhisperHelper` ويعرض `TranscribeScreen`، ثم يستدعي `freeModel()` عند الإتلاف.
- تعرض `TranscribeScreen.kt` مسار اختيار ملف ومسار تسجيل مباشر باستخدام `MediaRecorder`. ينسخ مسار اختيار الملف المحتوى إلى ملف مؤقت، ثم يستدعي `transcribeFile`; ويعرض النتيجة وأدوات التصدير/المشاركة والإعدادات في الواجهة.
- `WhisperHelper.kt` يتعامل مع الملفات الصوتية الصغيرة مباشرة، ويستخدم `MediaExtractor` و`MediaMuxer` لاستخراج مسار صوت من ملفات أخرى عند الإمكان، ثم يرمّز البيانات Base64 ويرسلها إلى Gemini عبر OkHttp. يُخزّن مفتاح مخصص في تفضيلات التطبيق تحت الاسم `custom_gemini_api_key`؛ لا تُدرج هنا أي قيمة مفتاح.
- `PdfExporter.kt` ينشئ PDF متعدد الصفحات في مجلد cache ويشاركه عبر `FileProvider`.
- `app/src/main/cpp/whisper_jni.cpp` و`CMakeLists.txt` يعرّفان مكتبة JNI مرتبطة بمكتبة whisper.cpp التي يجلبها CMake عبر FetchContent، إلا أن استخدامها من تدفق Kotlin الحالي غير مثبت.

### شجرة مختصرة
```text
.
├── README.md                         # وصف المشروع وتعليمات يدّعيها README
├── settings.gradle.kts               # إعداد Gradle وتضمين :app
├── build.gradle.kts                  # إصدارات إضافات Android وKotlin
├── gradle.properties                 # خصائص بناء عامة
├── gradle/wrapper/gradle-wrapper.properties # رابط Gradle 8.7؛ لا يظهر سكربت gradlew في الشجرة
├── .github/workflows/build-apk.yml   # CI لبناء APK ورفع artifact
└── app/
    ├── build.gradle.kts              # إعداد التطبيق والتبعيات وAndroid/CMake
    ├── proguard-rules.pro            # قواعد ProGuard
    └── src/main/
        ├── AndroidManifest.xml       # الأذونات ونشاط الإطلاق وFileProvider
        ├── java/com/katib/muhadarat/
        │   ├── MainActivity.kt       # نقطة دخول واجهة التطبيق
        │   ├── WhisperHelper.kt      # استخراج/رفع الصوت والتفريغ عبر Gemini
        │   ├── PdfExporter.kt        # إنشاء PDF ومشاركته
        │   └── ui/TranscribeScreen.kt # واجهة الملف والتسجيل والنتائج
        ├── cpp/
        │   ├── CMakeLists.txt        # جلب whisper.cpp وبناء المكتبة الأصلية
        │   └── whisper_jni.cpp       # واجهات JNI لمكتبة whisper
        └── res/                      # الأيقونات والثيمات والموارد
```

### التقنية والإصدارات والتبعيات
المشروع Android/Kotlin مع واجهة Jetpack Compose. وفق `build.gradle.kts` و`app/build.gradle.kts`: Android Gradle Plugin 8.5.2، Kotlin Android plugin 1.9.22، Gradle 8.7 وفق `gradle/wrapper/gradle-wrapper.properties`، `compileSdk` و`targetSdk` 34، `minSdk` 24، Java/JVM target 17، وإضافة Compose compiler 1.5.10. إصدار Compose BOM هو 2024.06.00. تشمل التبعيات المحددة Activity Compose 1.9.2 وCore KTX 1.13.1 وLifecycle Runtime KTX 2.8.6 وNavigation Compose 2.8.4 وOkHttp 4.12.0 وMedia3 Extractor/Transformer 1.4.1. إعداد CMake في Gradle هو 3.22.1؛ ملف CMake يتطلب 3.22 وC++17 ويجلب `ggml-org/whisper.cpp` بالوسم `b4938`. كما يحدد إعداد التطبيق ABI: `arm64-v8a` و`armeabi-v7a` و`x86_64`.

### الإعداد والتشغيل والبناء والاختبارات
- يحدد CI في `.github/workflows/build-apk.yml` بيئة Ubuntu، وJDK Temurin 17، وNDK `26.1.10909125`، وCMake `3.22.1`، وGradle `8.7`، ثم ينفذ `gradle assembleDebug --stacktrace` ويرفع APK من `app/build/outputs/apk/debug/*.apk`.
- قدّم README تعليمات Android Studio Hedgehog أو أحدث، وJDK 17 وNDK 26+ وCMake 3.22+، وأوامر `./gradlew` داخل `android/`؛ لكن مسار `android/` وسكربت `gradlew` غير ظاهرين في الشجرة الحالية، لذا لا يمكن اعتماد تلك الأوامر حرفيًا دون تصحيح/تحقق. مسار CI أعلاه هو أمر البناء الوحيد المثبت في الشفرة الحالية.
- لم يُعثر على ملفات ضمن `app/src/test/` أو `app/src/androidTest/` في الشجرة التي فُحصت. لا توثق هذه الملفات أمر اختبار.
- يعلن `README.md` تنزيل نموذج بحجم يقارب 465MB، لكن هذا الادعاء غير متسق مع تنفيذ `WhisperHelper.kt` الحالي؛ لا يُوثق هنا كخطوة إعداد مؤكدة.

### معلومات عملية ونقاط غير محسومة
- يتطلب مسار التفريغ الحالي اتصالًا بالشبكة وفق طلب Gemini الموجود في `WhisperHelper.kt`، كما يصرّح manifest بإذن `INTERNET` (`app/src/main/AndroidManifest.xml`). لا تفترض معالجة محلية أو خصوصية/احتفاظًا معينًا للبيانات دون توثيق إضافي.
- اسم إعداد مفتاح المستخدم المخزّن هو `custom_gemini_api_key` في SharedPreferences؛ لا توجد في الملفات التي فُحصت قائمة متغيرات بيئة لازمة. تجنب إدراج قيم بيانات اعتماد في التوثيق.
- توجد فجوة بين ملف native whisper وتجهيز FetchContent وبين التطبيق النشط، وفجوة بين مواقع التشغيل الموصوفة في README وبنية الجذر. يلزم توضيح ما إذا كان المقصود إعادة تفعيل Whisper محليًا أو اعتماد Gemini، والتحقق من الملفات/الأوامر الفعلية لاختبارات التطبيق قبل توثيقها.
- المراجع الأساسية لهذا الملف: `README.md`، `settings.gradle.kts`، `build.gradle.kts`، `app/build.gradle.kts`، `gradle/wrapper/gradle-wrapper.properties`، `.github/workflows/build-apk.yml`، `app/src/main/AndroidManifest.xml`، `app/src/main/java/com/katib/muhadarat/`، و`app/src/main/cpp/`.

## English

### Purpose and scope
This repository contains a native Android app named “Katib Al-Muhadarat” (Lecture Writer). It lets a user select an audio/video file or make a live audio recording, then displays a transcript and offers text handling and PDF export. Evidence: `README.md`, `app/src/main/java/com/katib/muhadarat/ui/TranscribeScreen.kt`, `app/src/main/java/com/katib/muhadarat/WhisperHelper.kt`, and `app/src/main/java/com/katib/muhadarat/PdfExporter.kt`.

**Documentation-versus-implementation caveat:** `README.md` describes local, offline Whisper processing after downloading a model, but the current execution path in `WhisperHelper.kt` builds HTTP requests to the Google Gemini API and sends audio data to it; it also contains a list of model names and fallback attempts. The current code therefore does not support the README’s offline-processing claim. The README also says to open `android/`, while the Gradle configuration and `app/` directory are at the repository root and no `android/` directory appears in the inspected tree. Native whisper.cpp integration exists under `app/src/main/cpp/`, but the current `WhisperHelper.kt` does not call the JNI methods defined in `whisper_jni.cpp`; `isModelReady()` also returns a constant. Resolve these discrepancies before treating the README as an authoritative setup guide.

### Main components and flow
- `MainActivity.kt` creates `WhisperHelper`, displays `TranscribeScreen`, and calls `freeModel()` during destruction.
- `TranscribeScreen.kt` provides file-selection and live-recording paths using `MediaRecorder`. The file path copies the selected content to a temporary file and calls `transcribeFile`; the UI presents the result plus export/share and settings controls.
- `WhisperHelper.kt` handles small audio files directly and attempts to extract an audio track from other files with `MediaExtractor` and `MediaMuxer`; it then Base64-encodes the data and sends it to Gemini with OkHttp. A custom key is stored in app preferences under `custom_gemini_api_key`; no key value is included here.
- `PdfExporter.kt` creates a multipage PDF in the cache directory and shares it through `FileProvider`.
- `app/src/main/cpp/whisper_jni.cpp` and `CMakeLists.txt` define a JNI library linked to whisper.cpp, fetched by CMake FetchContent. Use of this native implementation from the current Kotlin flow is not established.

### Condensed repository tree
```text
.
├── README.md                         # Project description and README-provided instructions
├── settings.gradle.kts               # Gradle setup and :app inclusion
├── build.gradle.kts                  # Android and Kotlin plugin versions
├── gradle.properties                 # General build properties
├── gradle/wrapper/gradle-wrapper.properties # Gradle 8.7 distribution URL; no gradlew script appears in the tree
├── .github/workflows/build-apk.yml   # APK CI build and artifact upload
└── app/
    ├── build.gradle.kts              # App, dependency, Android, and CMake configuration
    ├── proguard-rules.pro            # ProGuard rules
    └── src/main/
        ├── AndroidManifest.xml       # Permissions, launcher activity, and FileProvider
        ├── java/com/katib/muhadarat/
        │   ├── MainActivity.kt       # Application UI entry point
        │   ├── WhisperHelper.kt      # Audio extraction/upload and Gemini transcription
        │   ├── PdfExporter.kt        # PDF generation and sharing
        │   └── ui/TranscribeScreen.kt # File, recording, and result UI
        ├── cpp/
        │   ├── CMakeLists.txt        # Fetches whisper.cpp and builds the native library
        │   └── whisper_jni.cpp       # JNI interfaces for whisper
        └── res/                      # Icons, themes, and resources
```

### Technology, versions, and dependencies
The project is an Android/Kotlin app with a Jetpack Compose UI. According to `build.gradle.kts` and `app/build.gradle.kts`: Android Gradle Plugin 8.5.2, Kotlin Android plugin 1.9.22, Gradle 8.7 per `gradle/wrapper/gradle-wrapper.properties`, `compileSdk` and `targetSdk` 34, `minSdk` 24, Java/JVM target 17, and Compose compiler extension 1.5.10. The Compose BOM is 2024.06.00. Explicit dependencies include Activity Compose 1.9.2, Core KTX 1.13.1, Lifecycle Runtime KTX 2.8.6, Navigation Compose 2.8.4, OkHttp 4.12.0, and Media3 Extractor/Transformer 1.4.1. Gradle configures CMake 3.22.1; the CMake file requires 3.22, uses C++17, and fetches `ggml-org/whisper.cpp` at tag `b4938`. App ABI filters are `arm64-v8a`, `armeabi-v7a`, and `x86_64`.

### Setup, run, build, and tests
- CI in `.github/workflows/build-apk.yml` specifies Ubuntu, Temurin JDK 17, NDK `26.1.10909125`, CMake `3.22.1`, and Gradle `8.7`, then runs `gradle assembleDebug --stacktrace` and uploads `app/build/outputs/apk/debug/*.apk`.
- `README.md` specifies Android Studio Hedgehog or newer, JDK 17, NDK 26+, and CMake 3.22+, and gives `./gradlew` commands from `android/`; however, neither the `android/` path nor a `gradlew` script appears in the inspected tree, so those exact commands cannot be confirmed for the current checkout. The CI command above is the only build command established by the current repository files.
- No files under `app/src/test/` or `app/src/androidTest/` were found in the inspected tree. No test command is documented by those files.
- `README.md` claims that a roughly 465 MB model is downloaded, but this claim conflicts with the current `WhisperHelper.kt` implementation and is not documented here as a verified setup step.

### Practical notes and open questions
- The current transcription path requires network access according to the Gemini request in `WhisperHelper.kt`; the manifest also declares `INTERNET` (`app/src/main/AndroidManifest.xml`). Do not assume local processing or any particular data-retention/privacy behavior without further documentation.
- The custom user key setting is named `custom_gemini_api_key` in SharedPreferences. No required environment-variable list was found in the inspected files. Do not put credential values in documentation.
- There is a gap between the native whisper/FetchContent scaffolding and the active app flow, as well as a mismatch between the README’s setup location and the root project layout. Clarify whether the intended product is local Whisper or Gemini, and verify actual test files/commands before documenting them.
- Primary evidence paths for this profile: `README.md`, `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`, `.github/workflows/build-apk.yml`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/katib/muhadarat/`, and `app/src/main/cpp/`.
