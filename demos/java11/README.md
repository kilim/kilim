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

- need to call `Kilim.trampoline` in main
- all nested classes need to be loaded one way or another by the top-level class
- see `kilim.examples.Xorshift`, esp the first lines of `main`


