cmake_minimum_required(VERSION 3.22.1)

project("aichat")

# Create native library
add_library(
    aichat
    SHARED
    native-lib.cpp
)

# Find log library
find_library(
    log_lib
    log
)

# Link libraries
target_link_libraries(
    aichat
    ${log_lib}
)
