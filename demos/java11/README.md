# demo of JEP 330 single-file source invocation

kilim is able to run some source files.
JEP 330 class loading doesn't support all features, so source may need to be modified somewhat


## invocation

java 12:
```
cp=$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/fd/1)
$java12/bin/java -cp $cp ../../src/kilim/examples/Xorshift.java
```

for java 11, need an agent to access the bytecode:
```
cp=$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/fd/1)
kilim="-javaagent:${cp/:*} -cp $cp"
$java11/bin/java $kilim ../../src/kilim/examples/Xorshift.java
```

## caveats

- need to enable runtime weaving
- call `Kilim.trampoline` in main with a classloader template, eg:
  - `new Object() {}`
  - or `Xorshift.class`
- see `kilim.examples.Xorshift`, esp the first lines of `main`
- the bytecode was made available in java 12 via https://bugs.openjdk.java.net/browse/JDK-8210009



