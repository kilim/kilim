export CLASSPATH=./classes:./testclasses:./libs/asm-all-4.1.jar:./libs/slf4j-api-1.6.6.jar:./libs/slf4j-log4j12-1.6.6.jar:./libs/log4j-1.2.15.jar:./libs/junit.jar:$CLASSPATH 

echo making dir:  ./classes
rm -rf ./classes
rm -rf ./testclasses
mkdir ./classes
mkdir ./testclasses

echo Compiling java source ===========================================
javac -Xlint:unchecked -XDignore.symbol.file -g -d ./classes `find . -name "*.java" `

echo Compiling .j files for testing ==================================
java -ea kilim.tools.Asm -nf -d ./classes `find . -name "*.j"`

echo Weaving =========================================================
# Weave all files under ./classes, compiling the tests to
# ./testclasses while excluding any that match "ExInvalid". These are
# negative tests for the Weaver.
java -ea kilim.tools.Weaver -d ./classes -x "ExInvalid|test" ./classes
java -ea kilim.tools.Weaver -d ./testclasses -x "ExInvalid" ./classes


