# CedarJavaFFI

Bindings to allow calling the core Cedar functions (`is_authorized` and `validate`) from Java.

## Usage

### Build

You can build the code with

```shell
cargo build
```

Note that the `main` branch expects that the [cedar](https://github.com/cedar-policy/cedar) repository is cloned locally in the top-level directory (`..`). `release/x.x.x` branches use a version of `cedar-policy` available on [crates.io](https://crates.io/crates/cedar-policy).

### Run

You can test the code with

```shell
cargo test
```

To test methods in `interface.rs`, the code creates a JVM instance. If you encounter errors indicating that Java cannot be found, verify that the `JAVA_HOME` environment variable is properly set on your system. For more details about JVM initialization from Rust, see the [jni crate documentation](https://docs.rs/jni/latest/jni/struct.JavaVM.html#launching-jvm-from-rust).

Typically you will want to use `../CedarJava` in your project and won't care about `CedarJavaFFI`.

## Security

See [CONTRIBUTING](../CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.
