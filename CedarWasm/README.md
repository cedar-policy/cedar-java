# Cedar Wasm

Pure-Java Cedar policy engine using WebAssembly. The Cedar Rust crate is compiled to Wasm and executed via [Chicory Redline](https://github.com/AnyTimeTraveler/chicory-redline) — no JNI or native libraries required.

## Project structure

```
CedarWasm/
├── wasm-build/     Rust crate compiled to wasm32-unknown-unknown
├── core/           Java module (CedarEngine + Chicory Redline)
└── benchmark/      JMH benchmarks comparing JNI vs Wasm
```

## Prerequisites

- Java 17+
- Rust 1.89+ with `wasm32-unknown-unknown` target
- `wasm-opt` (from [binaryen](https://github.com/WebAssembly/binaryen))
- [Chicory Redline](https://github.com/AnyTimeTraveler/chicory-redline) installed to local Maven repo (`mvn install -DskipTests`)

## Building the Wasm module

```bash
cd wasm-build
rustup target add wasm32-unknown-unknown
cargo build --release --target wasm32-unknown-unknown
wasm-opt --enable-bulk-memory -O3 \
  target/wasm32-unknown-unknown/release/cedar_wasm.wasm \
  -o ../core/wasm/cedar_wasm.wasm
```

## Building and testing

```bash
cd CedarWasm
mvn clean test -pl core
```

## Running benchmarks (JNI vs Wasm)

First, build the JNI uber jar (requires Gradle + Rust):

```bash
cd CedarJava
./gradlew uberJar -x test -x spotbugsMain -x spotbugsTest -x spotbugsJmh \
  -x checkstyleMain -x checkstyleTest -x jacocoTestCoverageVerification
```

Then run the benchmarks:

```bash
cd CedarWasm
mvn install -DskipTests
mvn exec:java -pl benchmark
```

## Sample results

```
Benchmark                          Mode  Cnt      Score       Error  Units
CedarBenchmark.jniCachedLarge      avgt    3   1077.137 ±  2500.105  us/op
CedarBenchmark.jniCachedMedium     avgt    3     68.613 ±    44.063  us/op
CedarBenchmark.jniCachedSmall      avgt    3     29.142 ±     7.306  us/op
CedarBenchmark.jniCachedXLarge     avgt    3    476.329 ±  1036.358  us/op
CedarBenchmark.jniUncachedLarge    avgt    3   2209.292 ±  2208.573  us/op
CedarBenchmark.jniUncachedMedium   avgt    3    335.507 ±   269.147  us/op
CedarBenchmark.jniUncachedSmall    avgt    3     64.400 ±    20.911  us/op
CedarBenchmark.jniUncachedXLarge   avgt    3   9672.329 ±  1197.832  us/op
CedarBenchmark.wasmCachedLarge     avgt    3   2080.047 ±  1085.834  us/op
CedarBenchmark.wasmCachedMedium    avgt    3    218.603 ±   112.547  us/op
CedarBenchmark.wasmCachedSmall     avgt    3    131.787 ±   148.610  us/op
CedarBenchmark.wasmCachedXLarge    avgt    3    906.421 ±   664.702  us/op
CedarBenchmark.wasmUncachedLarge   avgt    3   2788.757 ±   364.868  us/op
CedarBenchmark.wasmUncachedMedium  avgt    3    840.366 ±   441.384  us/op
CedarBenchmark.wasmUncachedSmall   avgt    3    233.398 ±    41.424  us/op
CedarBenchmark.wasmUncachedXLarge  avgt    3  21890.498 ± 12325.095  us/op
```
