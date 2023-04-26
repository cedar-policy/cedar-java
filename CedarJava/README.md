# What?

This package provides the Java bindings for the Cedar policy evaluation language.
For more information, please see: https://www.cedarpolicy.com/en.

## (Internal) Development
This package depends on [Cedar](https://www.cedarpolicy.com/en), a library
that needs to be compiled so that it can be run in the used platform. So far, the easiest way to develop
is to build in Linux (either because you're using Linux or via DevDesktop/NinjaDevSync).

Building the package in Mac **doesn't work** unless you also add Cedar to your workspace
and build (and even this has proven to be error-prone so tread carefully...).

If you're encountering unexpected errors, a good first step in debugging can be to enable TRACE-level logging for
`cedarpolicy`, which will then show the exact messages being passed to Cedar. You can do this for
the unit tests by modifying the `test/resources/log4j2.xml` file; this file also gives an example for what to do in
other Log4j2-based packages.

Debugging calls across the JNI boundary is a bit tricky (as ever a bit more so on a Mac), but can be done by attaching
both a Java and native debugger (such as GDB/LLDB) to the program.

### TODO:
* Recommendations on how to set up remote debugging
* Better Mac/multi-platform story

# References
* Gradle User Guide Command-Line Interface: https://docs.gradle.org/current/userguide/command_line_interface.html
* Gradle User Guide Java Plugin: https://docs.gradle.org/current/userguide/java_plugin.html
* Gradle User Guide Checkstyle Plugin: https://docs.gradle.org/current/userguide/checkstyle_plugin.html
* Gradle User Guide SpotBugs Plugin: http://spotbugs.readthedocs.io/en/latest/gradle.html
* Gradle User Guide JaCoCo Plugin: https://docs.gradle.org/current/userguide/jacoco_plugin.html
* Authoring Gradle Tasks: https://docs.gradle.org/current/userguide/more_about_tasks.html
* Executing tests using JUnit5 Platform: https://junit.org/junit5/docs/current/user-guide/#running-tests-build-gradle and https://docs.gradle.org/4.6/release-notes.html#junit-5-support
