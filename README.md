# **Kilim**: Continuations, Fibers, Actors and message passing for the JVM

Kilim is composed of 2 primary components:
* The Kilim weaver modifies the bytecode of compiled java classes, enabling a method to save it's state
and yield control of it's thread, ie to cooperatively multitask
* The Kilim runtime library provides constructs that leverage the weaver to simplify concurrent programming, including Coroutines, Task, Actors, Mailboxes (aka channels), and Generators


Together, these facilities allow for simple concurrency and can scale to millions of concurrent tasks



## Usage

Code can be woven:
* during compilation by using the maven plugin
* during compilation by running `kilim.tools.Weaver`
* at runtime by invoking `kilim.tools.Kilim com.yourcompany.yourclass`
* at runtime by including `if (kilim.tools.Kilim.trampoline(false,args)) return;` at the start of main()




## Writing code with Kilim

Please see docs/manual.txt and docs/kilim_ecoop08.pdf for a brief
introduction.

* `src/kilim/examples` contains some simple examples
* `src/kilim/bench` contains some performance benchmarks
* [kilim streams](https://github.com/nqzero/kilim-streams) is a port of java 8 streams to kilim
* [java empower](https://github.com/nqzero/jempower) is a comparison of several async web server techs
  * `kj/src/main/java/KilimJetty.java` shows integrating with jetty async
  * `kilim/src/main/java/KilimHello.java` shows the built-in kilim web server (which in addition to doing async connections also does async io)

#### "hello world" in kilim

for an example of a project that uses kilim (with the trampoline for runtime weaving) see the
[battle royale demo in this repository](https://github.com/nqzero/kilim/tree/master/demos/battle).
clone this repository, and from that directory execute `mvn package exec:java -Dexec.mainClass=kilim.demo.Battle`

#### Similarity to Java Futures

[`java.util.concurrent.Future<V>` is an interface](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html) that can represents the result of an asynchronous computation.
kilim pre-dates java futures, and uses a slightly different api, but kilim supports the same notion, ie to block until an async computation finishes:

* `kilim.Task.joinb()` blocks until a `Task` completes
* `kilim.Task.isDone()` returns true if this `Task` completed
* cancellation isn't explicitly supported
* `kilim.Mailbox.getb()` is also similar

these methods allow synchronizing threads with async computations. however, the real power of kilim
is in enabling communication between async computations. for this, use the `join()` and `get()` methods
which are `Pausable`


## Maven

for an example of a project that uses kilim, see 
the [kilim jetty demo in this repository](https://github.com/nqzero/kilim/tree/master/demos/jetty)
- the `pom.xml` specifies kilim as both a dependency and a plugin for ahead-of-time weaving
- this version supports java 8, 9, 11, 12 and 13-ea (and 10 if you don't use lambdas)
- there are dedicated artifacts for java 7 and 10 (see below)

the dependency:

```
    <dependency>
        <groupId>org.db4j</groupId>
        <artifactId>kilim</artifactId>
        <version>2.0.1</version>
    </dependency>
```

weaving with the kilim plugin:

```
    <plugin>
        <groupId>org.db4j</groupId>
        <artifactId>kilim</artifactId>
        <version>2.0.1</version>
        <executions>
            <execution>
                <goals><goal>weave</goal></goals>
            </execution>
        </executions>
    </plugin>
```



## Overview

* java.lang.Runnable:   once a java.lang.Task starts running, it takes over the thread until it returns

* kilim.Continuation:  like java.lang.Runnable, but can yield (relinquish control of the thread) while preserving state. It’s caller must arrange to resume it at some appropriate time. Used for event-driven state machines where the event loop knows when to resume. The programmer is in charge of the event loop.

* kilim.Task:  like kilim.Continuation, but specifies a reason that it yields, and a scheduler automatically resumes it when the reason is no longer true. Typically used for a system of communicating state machines that communicate using Mailboxes. Releases the programmer from the job of mapping fibers to thread and of managing an event loop and the delivery of events.

* kilim.Pausable: a hypothetical exception used to declare intent. a method that declares this to be thrown will trigger the weaver

* kilim.Mailbox: a queue with Pausable methods that cause the calling Task method to yield when an operation would otherwise block and to resume automatically when the Mailbox because available for reading or writing

* kilim.Fiber: the structure that stores the Continuation (and Task) state



## Java Versions

java 8, 11 and 12 are the recommended platforms, but 7, 8, 9, and 10 are regularly tested, and in theory java 6 could probably still be made to work without too much work

java 8, java 9, java 11, java 12 and java 13-ea:
  * maven central: `org.db4j : kilim : 2.0.2`
  * compiled with java 8 bytecode
  * ASM 7.1 supports all versions of java through java 13-ea (and presumably, java 13 when it's released)


### other versions and notes on limitations

java 7:
  * `JAVA_HOME=path/to/java7 ant clean weave jar`
  * see `demos/java7` for usage examples
  * some features are not available, eg jetty integration and lambdas
  * this version is incompatible with lambdas in later java versions because default interface methods aren't supported
  * maven central: `2.0.1-jdk7`

java 9:
  * the java 8 compiled version supports java 9
  * see demos/battle/pom9.xml for a usage example
  * kilim does not explicitly support modules, but works with the builtin fallback support, see below
  * JShell works - see demos/jshell

java 10:
  * java 10 has a bug and refuses to load some valid lambdas
  * https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8209112
  * this is already fixed in java 11 but will not be fixed in java 10 due to the new release cycle
  * throws `java.lang.NoClassDefFoundError` when attempting to load a woven fiber-less lambda
  * workaround: use lambdas that take an explicit last parameter `Fiber dummy`
      and a corresponding default method without that last parameter
  * the `Fiber` argument should not be accessed in the lambda
  * to call a `Pausable` lambda, call the default method
  * github tag: java10
  * all lambdas that will be loaded need to be woven with this java10 "fiber-included" flavor of kilim
  * this will work fine with java 8 or 9 as well, however it exposes the fiber to the user and
      makes it harder to detect unwoven Pausable methods, so it's use is discouraged unless you need to support java 10
  * maven central: `2.0.0-28-jdk10`

```
    interface Lambda {
        void execute(Fiber fiber) throws Pausable, Exception;
        default void execute() throws Pausable, Exception {}
    }
    static void dummy() throws Pausable, Exception {
        Lambda lambda = dummy -> {
            Task.sleep(1000);
            System.out.println("hello world");
        };
        lambda.execute();
    }
```

java 11:
  * constant dynamics and preview classes are not yet supported - they will be when usage in the wild is seen
  * JEP 330 single-file source invocation works with java 11
    * the JEP 330 class loader has some limitations which must be worked around
    * for java 11, use the kilim jar as a `-javaagent` (fixed in java 12)
    * see `demos/java11`


### specific java features

lambdas:
 * working
 * serialization of woven lambdas is problematic and possibly cannot be supported generally
 * http://mail.openjdk.java.net/pipermail/core-libs-dev/2014-July/027726.html

jshell:
 * working
 * see `demos/jshell` for an example of automatic weaving

JEP-330 single file source invocation
 * working
 * see `demos/java11` for an example
 * for java 11, a java agent (included) is needed, see above (this is fixed in java 12)
 * call `Kilim.trampoline` in main to enable weaving
 * eg for java 12:
```
    public static void main(String[] args) {
        if (kilim.tools.Kilim.trampoline(new Object() {},false,args)) return;
    ...
```
 

modules:
 * kilim works with java 9 and later using the builtin fallback support
 * no `module-info.java` is provided
    * if you have a demo project that you can share that "depends" on modules, create an issue and it will be investigated

Project Loom:
 * Project Loom is not yet released and is not yet supported by kilim
   * some testing has been done
 * Loom attempts to run a much larger subset of the java language than kilim,
     at least initially at the expense of performance
 * when it is released, kilim will integrate with Loom fibers in whatever capacity makes sense


## Building


summary:

* maven and ant are used cooperatively
* maven is used for downloading dependencies (or manually copy them to ./libs - see pom.xml)
  * only needs to be done once (until dependencies change)
  * `mvn initialize` (but any pom-based mvn command should work too)
  * `and cleaner` to delete the copied jars
* maven can also be used for building, but tests are disabled
* there's a kilim maven plugin, but it's not used here to avoid a circular dependency - the weaver is run directly instead (using ant)
* the plugin is only built during the maven build, but once built will be packaged by ant as well, eg `mvn package && ant jar`

simple:
`mvn install`

build with tests:
`ant clean testjit test jar doc`

details:
* testjit runs the tests (after compiling) using the runtime weaver
* test runs the tests using the compile-time weaver, as well as some tests that don't require weaving
* doc generates sources.jar and javadoc.jar
* `mvn install:install-file -DpomFile=pom.xml -Dfile=$(target/kilim*.jar) -Dsources=target/sources.jar -Djavadoc=target/javadoc.jar`



## Support

public support:
* [the mailing list](https://groups.google.com/forum/#!forum/kilimthreads)
* [github issues](https://github.com/nqzero/kilim/issues)



nqzero is currently the primary maintainer.
he's focused on making Kilim easy to use and reliable and is actively using Kilim

Sriram is the original author (in 2006 !) and deserves a great deal of thanks for his excellent work.
He continues to provide guidance on kilim and is especially interested in theory and performance,
but is not actively using kilim today.
He can be reached at kilim _at_ malhar.net


## Users

a number of companies (or their employees) appear to have been using Kilim recently and have contributed
* [didichuxing.com](https://github.com/taowen/kilim)
* [alipay](https://github.com/pfmiles/kilim-fiber)
* [hedvig](https://github.com/kilim/kilim/commit/9b428b16489a87bc783f44052eebd0b45ed45a0d)


## Copyright and License


Kilim v2.0
* Copyright (c) 2006, 2014 Sriram Srinivasan (kilim _at_ malhar.net)
* Copyright (c) 2016 nqzero
* Copyright (c) 2013 Nilang Shah
* Copyright (c) 2013 Jason Pell
* Copyright (c) 2013 Jestan Nirojan (maven plugin)

This software is released under an MIT-style license (please see the
License file). Unless otherwise noted, all files in this distribution are
offered under these terms, and files that explicitly refer to the "MIT License"
refer to this license


