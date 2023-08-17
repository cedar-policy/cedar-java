# cedar-java

This repository contains the source code for a Java package `CedarJava` that supports using the [Cedar](https://www.cedarpolicy.com) policy language.
It also contains source code for a Rust crate `CedarJavaFFI` that enables calling Cedar library functions (written in Rust) from Java.

You can find build instructions and more information in the subfolders.

## Notes

You need JDK 17 or later to run the Java code.

Cedar is primarily developed in Rust (in the [cedar](https://github.com/cedar-policy/cedar) repository). As such, `CedarJava` typically lags behind the newest Cedar features. Notably, as of this writing, `CedarJava` does not expose APIs for partial evaluation.

The `main` branch of this repository is kept up-to-date with the development version of the Rust code (available in the `main` branch of [cedar](https://github.com/cedar-policy/cedar)). Unless you plan to build the Rust code locally, please use the latest `release/x.x.x` branch instead.

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.
