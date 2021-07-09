# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# build with secret salt only if file is in the correct directory
if (EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/release-lib.cpp")
add_library( release-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/release-lib.cpp )
endif()

add_library( debug-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/debug-lib.cpp )

