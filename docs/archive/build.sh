export CLASSPATH=./classes:./testclasses:./libs/\*:$CLASSPATH 

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


