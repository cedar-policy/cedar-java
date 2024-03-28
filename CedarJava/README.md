# CedarJava

This package provides the Java interface for the Cedar language. You can use these to call Cedar from your Java applications. See [java-hello-world](https://github.com/cedar-policy/cedar-examples/tree/main/cedar-java-hello-world) for an example of calling Cedar from a Java application.

For more information about Cedar, please see: https://www.cedarpolicy.com/

## Prerequisites

- [JDK 17](https://openjdk.org/projects/jdk/17/) or later
- [Rust](https://rustup.rs/) with `rustup`
- [Zig](https://ziglang.org/learn/getting-started/) for cross compiling with [cargo-zigbuild](https://github.com/rust-cross/cargo-zigbuild)

## Building

Run the [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper_basics.html)
with the `build` task to compile both the Cedar Java Foreign Function Interface and the Cedar Java library.

```shell
./gradlew build
```

## Debugging

Debugging calls across the JNI boundary is a bit tricky (as ever a bit more so on a Mac), but can be done by attaching
both a Java and native debugger (such as GDB/LLDB) to the program.

## Unsupported Features
You can see a list of features not yet supported in CedarJava at [Differences from Rust](DIFFERENCES_FROM_RUST.md).

## Security

See [CONTRIBUTING](https://github.com/cedar-policy/cedar-java/tree/main/CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.
