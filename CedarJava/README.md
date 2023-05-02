# What?

This package provides the Java bindings for the Cedar policy evaluation language.
For more information, please see: https://www.cedarpolicy.com/

## Development
This package depends on [Cedar](https://www.cedarpolicy.com/), a library
that needs to be compiled so that it can be run on the used platform.

To see the exact steps required for building, we recommend looking at `.github/workflows/ci.yml`

TLDR; you can build as follows.

- Ensure Rust, Gradle and a JDK are installed.
- clone `cedar-policy/cedar` into `cedar-java/cedar` (you don't have to build it)
- then:
```
cd CedarJavaFFI
cargo build
cargo test
cd ../CedarJava
./gradlew build
```
## Debugging

If you're encountering unexpected errors, a good first step in debugging can be to enable TRACE-level logging for
`cedarpolicy`, which will then show the exact messages being passed to Cedar. You can do this for
the unit tests by modifying the `test/resources/log4j2.xml` file; this file also gives an example for what to do in
other Log4j2-based packages.

Debugging calls across the JNI boundary is a bit tricky (as ever a bit more so on a Mac), but can be done by attaching
both a Java and native debugger (such as GDB/LLDB) to the program.

