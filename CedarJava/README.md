## CedarJava

This package provides the Java interface for the Cedar language. You can use these to call Cedar from your Java applications. See [java-hello-world](https://github.com/cedar-policy/cedar-examples/tree/main/cedar-java-hello-world) for an example of calling Cedar from a Java application.

For more information about Cedar, please see: https://www.cedarpolicy.com/

## Usage
This package depends on [Cedar](https://www.cedarpolicy.com/), a library
that needs to be compiled so that it can be run on the used platform.

You need to ensure the `CEDAR_JAVA_FFI_LIB` variable is set correctly. Typically running `config.sh` will set this for you.
### Building
- Ensure Rust, Gradle and a JDK are installed.
- clone `cedar-policy/cedar` into `cedar-java/cedar` (you don't have to build it)
- then:
```shell
cd CedarJavaFFI
cargo build
cargo test
cd ../CedarJava
bash config.sh
./gradlew build
```

This will run the tests as well.

## Debugging

If you're encountering unexpected errors, a good first step in debugging can be to enable TRACE-level logging for
`cedarpolicy`, which will then show the exact messages being passed to Cedar. You can do this for
the unit tests by modifying the `test/resources/log4j2.xml` file; this file also gives an example for what to do in
other Log4j2-based packages.

Debugging calls across the JNI boundary is a bit tricky (as ever a bit more so on a Mac), but can be done by attaching
both a Java and native debugger (such as GDB/LLDB) to the program.

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

