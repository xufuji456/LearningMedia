# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)
set(distribution_DIR ${CMAKE_SOURCE_DIR}/../../../../libs)

add_library(faac
             SHARED
             IMPORTED)
set_target_properties(faac
                       PROPERTIES IMPORTED_LOCATION
                       ../../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libfaac.so)

add_subdirectory(src/main/cpp/rtmp)

add_library(x264
            STATIC
            IMPORTED)
set_target_properties(x264
                      PROPERTIES IMPORTED_LOCATION
                      ../../../../libs/${CMAKE_ANDROID_ARCH_ABI}/libx264.a)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")

include_directories(src/main/cpp/include)

aux_source_directory(src/main/cpp SRC_LIST)

add_library(live
        SHARED
        ${SRC_LIST})

find_library( log-lib
              log )

target_link_libraries(live x264 faac rtmp
                       -landroid
                       -ljnigraphics
                       ${log-lib} )