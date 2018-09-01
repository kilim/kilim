# demo of JEP 330 single-file source invocation

kilim is able to run some source files.
JEP 330 class loading doesn't support all features, so source may need to be modified somewhat


## invocation

```
cp=$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/fd/1)
kilim="-javaagent:${cp/:*} -cp $cp"
$java11/bin/java $kilim ../../src/kilim/examples/Xorshift.java
```


## caveats

- need to call `Kilim.trampoline` in main with a classloader template, eg:
  - `new Object() {}`
  - or `Xorshift.class`
- see `kilim.examples.Xorshift`, esp the first lines of `main`


