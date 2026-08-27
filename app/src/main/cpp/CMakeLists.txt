cmake_minimum_required(VERSION 3.22)
project(katib-whisper)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── جلب whisper.cpp من GitHub عند البناء (لا حاجة لنسخه يدويًا) ──
include(FetchContent)
FetchContent_Declare(
    whisper
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG        b4938
    GIT_SHALLOW    TRUE
)
# لا تبني الأدوات/الأمثلة، فقط المكتبة
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(whisper)

add_library(katib-whisper SHARED
    whisper_jni.cpp
)

target_link_libraries(katib-whisper
    whisper
    android
    log
)

target_include_directories(katib-whisper PRIVATE
    ${whisper_SOURCE_DIR}/include
    ${whisper_SOURCE_DIR}/ggml/include
)
