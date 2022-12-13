cmake_minimum_required(VERSION 3.4.1)

set(ffmpeg_dir ${CMAKE_SOURCE_DIR}/../ffmpeg-android-maker/output)
set(ffmpeg_libs ${ffmpeg_dir}/lib/${ANDROID_ABI})

include_directories(${ffmpeg_dir}/include/${ANDROID_ABI})

set(
        # List variable name
        ffmpeg_libs_names
        # Values in the list
        avutil avformat avcodec swscale swresample)

foreach (ffmpeg_lib_name ${ffmpeg_libs_names})
    add_library(
            ${ffmpeg_lib_name}
            SHARED
            IMPORTED)
    set_target_properties(
            ${ffmpeg_lib_name}
            PROPERTIES
            IMPORTED_LOCATION
            ${ffmpeg_libs}/lib${ffmpeg_lib_name}.so)
endforeach ()

#==============================================================

add_library( # Sets the name of the library.
         native-lib

         # Sets the library as a shared library.
         SHARED

         # Provides a relative path to your source file(s).
         # Associated headers in the same location as their source
         # file are automatically included.
         src/main/cpp/duktape.c
         src/main/cpp/ffmpeg_wrapper.c
         src/main/cpp/duktape_wrapper.c)
         
find_library( # Sets the name of the path variable.
          log-lib

          # Specifies the name of the NDK library that
          # you want CMake to locate.
          log )
target_link_libraries( # Specifies the target library.
                   native-lib
                   # Links the target library to the log library
                   # included in the NDK.
                   ${log-lib} 

                   ${ffmpeg_libs_names})