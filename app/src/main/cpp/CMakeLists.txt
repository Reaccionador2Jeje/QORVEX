cmake_minimum_required(VERSION 3.22.1)
project(qorvex_native LANGUAGES CXX)

add_library(qorvex_native SHARED qorvex_native.cpp)
target_compile_features(qorvex_native PRIVATE cxx_std_17)
target_compile_options(qorvex_native PRIVATE -Wall -Wextra -Werror)
target_link_libraries(qorvex_native android log)
