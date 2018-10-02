#! /bin/bash

pom=$1

[[ -f "$pom" ]] || {
    echo
    echo "usage: testing.sh pom"
    echo "  run the kilim test suite as an external project"
    echo "    with both runtime and ahead-of-time weaving"
    echo "  install the resulting woven artifact"
    echo "  and run the tests again as a dependency (see pom-test.xml)"
    echo
    echo "options:"
    echo "  pom: required, a valid maven pom.xml"
    echo "env:"
    echo "  JAVA_HOME: required, used by maven and to run ./bin/java"
    echo "  MAVEN_HOME: optional, added to the path if set"
    echo "exit status:"
    echo "  success if testjit and test both succeed"
    echo
    exit 1;
}

set -e

if [ -n "$MAVEN_HOME" ]; then
    PATH="$MAVEN_HOME/bin:$PATH"
fi


mvn -f $pom -q clean compile
cp=$(mvn -f $pom -q dependency:build-classpath -Dmdep.outputFile=/dev/fd/1)

$JAVA_HOME/bin/java -ea -cp $cp kilim.tools.Asm -q -nf -d target/classes $(find src/ -name "*.j") > /dev/null

mvn -f $pom -q exec:java -Dexec.mainClass=kilim.tools.Kilim -Dexec.args="junit.textui.TestRunner kilim.test.AllWoven"

mvn -f $pom -q install

mvn -f $pom -q exec:java -Dexec.mainClass=junit.textui.TestRunner -Dexec.args="kilim.test.AllWoven"

tpom=$(dirname $pom)/pom-test.xml
mvn -f $tpom -q exec:java

