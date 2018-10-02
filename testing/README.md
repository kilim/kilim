testing
=======

the kilim tests are run as part of the build
* that build environment differs from how user code is used
* to simulate user code, this directory enables running those tests outside the kilim build


`testing.sh`:
* runs the kilim tests as an external project, with both runtime and ahead of time weaving
* installs them as a woven artifact
* runs them again as a dependency
* ie, in the same way that user code is run
* with a specified jdk home and pom


note: `pom-test.xml` is for testing the installed artifact and is not a valid argument

eg, run:

    JAVA_HOME=$java11 ./testing.sh pom11.xml



