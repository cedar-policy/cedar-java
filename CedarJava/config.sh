#!/bin/bash

# If we run with anything but 1 or 2 args, exit with error code 1
if [ "$#" -ne 0 ] && [ "$#" -ne 1 ]; then
    echo "Wrong number of args:" $#
    exit 1;
fi

parent_dir="$(dirname $(pwd))"

#Try to set correctly for Mac and Linux machines
if [ "$(uname)" == "Darwin" ]; then
    ffi_lib_str="    environment 'CEDAR_JAVA_FFI_LIB', '"$parent_dir"/CedarJavaFFI/target/debug/libcedar_java_ffi.dylib'"
else
    ffi_lib_str="    environment 'CEDAR_JAVA_FFI_LIB', '"$parent_dir"/CedarJavaFFI/target/debug/libcedar_java_ffi.so'"
fi
sed "83s;.*;$ffi_lib_str;" "build.gradle" > new_build.gradle
mv new_build.gradle build.gradle

# In CI, we need to pull the latest cedar-policy to match the latest cedar-integration-tests
# We require that integration tests be run
# Outside of CI, we can skip the integration tests (run script with no args)
# If you call this script with `run_int_tests`, we assume you have `cedar` checkout out in the `cedar-java` dir
if [ "$#" -ne 0 ] && [ "$1" == "run_int_tests" ]; then
    integration_tests_str="    environment 'CEDAR_INTEGRATION_TESTS_ROOT', '"$parent_dir"/cedar/cedar-integration-tests'"
    sed "82s;.*;$integration_tests_str;" "build.gradle" > new_build.gradle
    mv new_build.gradle build.gradle

    export MUST_RUN_CEDAR_INTEGRATION_TESTS=1

    cargo_str='cedar-policy = { version = "2.3", path = "../cedar/cedar-policy" }'
    sed "12s;.*;$cargo_str;" "../CedarJavaFFI/Cargo.toml" > new_Cargo.toml
    mv new_Cargo.toml ../CedarJavaFFI/Cargo.toml
else
    unset MUST_RUN_CEDAR_INTEGRATION_TESTS
    export CEDAR_INTEGRATION_TESTS_ROOT=/tmp
fi

tail build.gradle
cat ../CedarJavaFFI/Cargo.toml
ls /home/runner/work/cedar-java/cedar-java/cedar/cedar-integration-tests
