# an example of using java 7
# java 8 is the standard environment for kilim, but java 7 should still work
# however, jars are no currently staged in maven central so you need to install locally
# note: java 6 should work too but this is untested - i don't have a dev environment for it
# some features depend on java 8, including the jetty support

# this demo works by using the jdk7 classifier in the pom to select between java 7 and 8 versions of kilim



# in the toplevel kilim directory, build and install with java 7
JAVA_HOME=$java7 ant clean weave jar
mvn install:install-file -DpomFile=pom.xml -Dfile=target/kilim.jar -Dclassifier=jdk7







# in this directory

mvn clean package
JAVA_HOME=$java7 mvn exec:java -Dexec.mainClass=kilim.demo.Battle







