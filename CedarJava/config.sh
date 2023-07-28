#!/bin/bash

parent_dir="$(dirname $(pwd))"

#Try to set correctly for Mac and Linux machines
if [ "$(uname)" == "Darwin" ]; then
ffi_lib_str="    environment 'CEDAR_JAVA_FFI_LIB', '"$parent_dir"/CedarJavaFFI/target/debug/libcedar_java_ffi.dylib'"
else
ffi_lib_str="    environment 'CEDAR_JAVA_FFI_LIB', '"$parent_dir"/CedarJavaFFI/target/debug/libcedar_java_ffi.so'"
fi
sed "83s;.*;$ffi_lib_str;" "build.gradle" > new_build.gradle
mv new_build.gradle build.gradle

int_tests_str="    environment 'CEDAR_INTEGRATION_TESTS_ROOT', '"$parent_dir"/cedar/cedar-integration-tests'"
sed "82s;.*;$int_test_str;" "build.gradle" > new_build.gradle
mv new_build.gradle build.gradle

export MUST_RUN_CEDAR_INTEGRATION_TESTS=1

tail build.gradle

ls ..
ls ../..