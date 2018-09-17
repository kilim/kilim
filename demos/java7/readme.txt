# an example of using java 7
# java 8 is the standard environment for kilim, but java 7 should still work
# note: java 6 should work too but this is untested - i don't have a dev environment for it
# some features depend on java 8, including the jetty support
# using maven plugins with a classifier doesn't work, so jdk-specific version numbers are used


# build and run the demo
JAVA_HOME=$java7 mvn -Dhttps.protocols=TLSv1.2 clean package exec:java -Dexec.mainClass=kilim.demo.Battle


# not all versions are available in maven central compiled for java 7, so you may need to install locally
# in the toplevel kilim directory, build and install with java 7

version=2.0.0-25-jdk7
mvn versions:set -DnewVersion=$version
JAVA_HOME=$java7 ant clean weave jar
mvn install:install-file -DpomFile=pom.xml -Dfile=target/kilim.jar









