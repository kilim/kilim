demo of using kilim and runtime weaving with jshell, ie defining pausable methods inside jshell


here's a transcript:


```
mvn compile
cp=$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/fd/1)
$java9/bin/java -cp target/classes:$cp Kshell



jshell> import kilim.*;

jshell> Mailbox<Integer> mb = new Mailbox<>();
mb ==> id:729803618 numMsgs:0

jshell> Task.fork(() -> { for (int ii=0; ii < 5; ii++) { Task.sleep(1000); mb.put(ii); } })
$39 ==> 24(running=true,pr=null)

jshell> for (Integer val=0; val != null;) System.out.println(val = mb.getb(2000));
0
1
2
3
4
null

jshell> /exit
|  Goodbye
```


notes:

* jshell (not kilim) creates a timer thread that doesn't exit cleanly, and maven would wait for it.
so i call `System.exit` instead

* the toplevel jshell method is not pausable (similar to `main()`) so it's not possible to call pausable methods directly

* use `Task.fork` or `Task.spawn` or `new Task() {}.start()` to start a task

* use blocking methods to access mailboxes and tasks, eg `mb.getb(2000)` or `task.joinb()`

* calling pausable methods directly causes a `KilimException` and causes jshell to terminate. since this can be inconvenient, it may be handled more gracefully in the future

* there's a java 10 bug that openjdk expects to fix in 10.3 (expected mid october) that affects some kilim lambdas. use a `-jdk10` versioned artifact in the meantime if you experience errors such as `java.lang.NoClassDefFoundError thrown: REPL/$JShell$17$$Lambda$437`










