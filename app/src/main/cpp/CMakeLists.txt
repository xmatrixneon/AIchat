cmake_minimum_required(VERSION 3.22.1)

project("aichat")

# Create encryption library
add_library(
    aicrypt
    SHARED
    aicrypt.cpp
)

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

# Link libraries for aicrypt
target_link_libraries(
    aicrypt
    ${log_lib}
    dl
)

# Link libraries for aichat
target_link_libraries(
    aichat
    ${log_lib}
)
