# cedar-java
![Cedar Logo](https://github.com/cedar-policy/cedar/blob/main/logo.svg)  

This repository contains the source code for a Java package `CedarJava` that supports using the [Cedar](https://www.cedarpolicy.com) policy language. It also contains source code for a Rust crate `CedarJavaFFI` that enables calling Cedar library functions (written in Rust) from Java.

Cedar is a language for writing and enforcing authorization policies in your applications. Using Cedar, you can write policies that specify your applications' fine-grained permissions. Your applications then authorize access requests by calling Cedar's authorization engine. Because Cedar policies are separate from application code, they can be independently authored, updated, analyzed, and audited. You can use Cedar's validator to check that Cedar policies are consistent with a declared schema which defines your application's authorization model.


## Getting Started

### Import `CedarJava` to your application
#### Maven Package
CedarJava is available as a maven package. You can add `CedarJava` as a dependency to your build file.  

Example (Gradle): 
```
dependencies{
    implementation 'com.cedarpolicy:cedar-java:4.2.3:uber'
}
```
We highly recommend using the `*-uber.jar` as it also contains the shared library from `CedarJavaFFI`.

See [https://central.sonatype.com/artifact/com.cedarpolicy/cedar-java](https://central.sonatype.com/artifact/com.cedarpolicy/cedar-java) for more details.  

#### Build from Source

The [CedarJavaFFI](https://github.com/cedar-policy/cedar-java/blob/main/CedarJavaFFI/README.md) and [CedarJava](https://github.com/cedar-policy/cedar-java/blob/main/CedarJava/README.md) directories contain detailed instructions on building the individual modules.

The `CedarJava` module uses Gradle to build both modules and run integration tests. It stores the shared library from `CedarJavaFFI` in the `-uber.jar`. The following commands provide general usage for getting started.

```shell
cd CedarJava
./gradlew build
```

### Perform an Authorization Request
Here is a small snippet on how to perform `isAuthorized()` call using `CedarJava`
```java
package com.mypackage;

import com.cedarpolicy.AuthorizationEngine;
import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.Context;
import com.cedarpolicy.model.entity.Entities;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.value.EntityUID;

public class SimpleAuthorization {
    public static void main(String[] args) throws Exception {

        // Build entities
        Entity principal = new Entity(EntityUID.parse("User::\"Alice\"").get());
        Entity action = new Entity(EntityUID.parse("Action::\"view\"").get());
        Entity resource = new Entity(EntityUID.parse("Photo::\"alice_photo\"").get());

        // Build policies
        PolicySet policySet = PolicySet.parsePolicies("""
            permit(
                principal == User::"Alice",
                action == Action::"view",
                resource == Photo::"alice_photo"
            );

            forbid(
                principal == User::"Alice",
                action == Action::"view",
                resource == Photo::"bob_photo"
            );
        """);
        
        // Authorization request
        AuthorizationEngine ae = new BasicAuthorizationEngine();
        Entities entities = new Entities();
        Context context = new Context();
        AuthorizationRequest request  = new AuthorizationRequest(principal, action, resource, context);
        AuthorizationResponse authorizationResponse = ae.isAuthorized(request, policySet, entities);
    }
}
```

## Examples
Explore our sample applications in [cedar-examples](https://github.com/cedar-policy/cedar-examples/tree/main):
* [**cedar-java-hello-world**](https://github.com/cedar-policy/cedar-examples/tree/main/cedar-java-hello-world): Demonstrates basic authorization calls using Cedar-Java
* [**cedar-java-partial-evaluation**](https://github.com/cedar-policy/cedar-examples/tree/main/cedar-java-partial-evaluation): Illustrates partial evaluation capabilities in Cedar-Java

## Changelog
For a list of changes and version updates, see [CHANGELOG.md](CedarJava/CHANGELOG.md).

## Notes

`CedarJava` requires JDK 17 or later.

Cedar is primarily developed in Rust (in the [cedar](https://github.com/cedar-policy/cedar) repository). As such, `CedarJava` typically lags behind the newest Cedar features. 

The `main` branch of this repository is kept up-to-date with the development version of the Rust code (available in the `main` branch of [cedar](https://github.com/cedar-policy/cedar)). Unless you plan to build the Rust code locally, please use the latest `release/x.x.x` branch instead.

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.
