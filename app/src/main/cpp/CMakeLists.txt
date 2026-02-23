cmake_minimum_required(VERSION 3.22.1)
project(hybridassistant)

add_library(hybridassistant_jni SHARED hybridassistant_jni.cpp)

find_library(log-lib log)

target_include_directories(hybridassistant_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_compile_definitions(hybridassistant_jni PRIVATE ANDROID_NDK)

set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp)
if (EXISTS ${LLAMA_DIR}/CMakeLists.txt)
    message(STATUS "llama.cpp source detected, enabling JNI bridge with llama backend")
    add_subdirectory(${LLAMA_DIR} llama_build)
    target_compile_definitions(hybridassistant_jni PRIVATE HYBRID_LLAMA_ENABLED=1)
    if (TARGET llama)
        target_link_libraries(hybridassistant_jni llama ${log-lib})
    else()
        target_link_libraries(hybridassistant_jni ${log-lib})
    endif()
else()
    message(WARNING "llama.cpp not found under app/src/main/cpp/third_party/llama.cpp, building stub JNI only")
    target_link_libraries(hybridassistant_jni ${log-lib})
endif()
