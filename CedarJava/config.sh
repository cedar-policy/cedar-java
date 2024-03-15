#!/bin/bash

# If we run with anything but 1 or 2 args, exit with error code 1
if [ "$#" -ne 0 ] && [ "$#" -ne 1 ]; then
    echo "Wrong number of args:" $#
    exit 1;
fi

# Find and replace a pattern with some text in a file using `sed`, but first
# check that the pattern occurs in the file exactly once.
replace_once() {
    local replace_in_file="$1"
    local pattern="$2"
    local replace_with="$3"

    local match_count
    match_count="$(grep -c "$pattern" "$replace_in_file")"
    if [ "$match_count" -ne 1 ]; then
        echo "Expected $pattern would match exactly once in $replace_in_file, but it matched $match_count times"
        exit 1
    fi

    sed -i.bak "s;$pattern;$replace_with;" "$replace_in_file"
    rm "$replace_in_file".bak
}

parent_dir="$(dirname $(pwd))"

#Try to set correctly for Mac and Linux machines
if [ "$(uname)" == "Darwin" ]; then
    ffi_lib_str="    environment 'CEDAR_JAVA_FFI_LIB', '"$parent_dir"/CedarJavaFFI/target/debug/libcedar_java_ffi.dylib'"
else
    ffi_lib_str="    environment 'CEDAR_JAVA_FFI_LIB', '"$parent_dir"/CedarJavaFFI/target/debug/libcedar_java_ffi.so'"
fi
replace_once build.gradle ".*CEDAR_JAVA_FFI_LIB.*" "$ffi_lib_str"

# In CI, we need to pull the latest cedar-policy to match the latest cedar-integration-tests
# We require that integration tests be run
# Outside of CI, we can skip the integration tests (run script with no args)
# If you call this script with `run_int_tests`, we assume you have `cedar` checkout out in the `cedar-java` dir
if [ "$#" -ne 0 ] && [ "$1" == "run_int_tests" ]; then
    integration_tests_str="    environment 'CEDAR_INTEGRATION_TESTS_ROOT', '"$parent_dir"/cedar/cedar-integration-tests'"
    replace_once build.gradle ".*CEDAR_INTEGRATION_TESTS_ROOT.*" "$integration_tests_str"

    export MUST_RUN_CEDAR_INTEGRATION_TESTS=1

    cargo_str='cedar-policy = { version = "4.0.0", path = "../cedar/cedar-policy" }'
    replace_once ../CedarJavaFFI/Cargo.toml ".*cedar-policy =.*" "$cargo_str"
else
    unset MUST_RUN_CEDAR_INTEGRATION_TESTS
    export CEDAR_INTEGRATION_TESTS_ROOT=/tmp
fi
