# **Kilim**: Continuations, Fibers, Actors and message passing for the JVM

Kilim is composed of 2 primary components:
* The Kilim weaver modifies the bytecode of compiled java classes, enabling a method to save it's state
and yield control of it's thread, ie to cooperatively multitask
* The Kilim runtime library provides constructs that leverage the weaver to simplify concurrent programming, including Coroutines, Task, Actors, Mailboxes (aka channels), and Generators


Together, these facilities allow for simple concurrency and can scale to millions of concurrent tasks



## Usage

Code can be woven:
* during compilation by running `kilim.tools.Weaver`
* during compilation by using the `kilim-maven-plugin`
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


## Maven



```
<dependency>
    <groupId>org.db4j</groupId>
    <artifactId>kilim</artifactId>
    <version>2.0.0-3</version>
</dependency>
```

## Building


summary:

* the primary build/test environment is ant
* maven is used for downloading dependencies (or manually copy them to ./libs - see pom.xml)
  * only needs to be done once
  * `mvn initialize` (but any pom-based mvn command should work too)
* maven can also be used for building, but tests are disabled
* there's a kilim-maven-plugin, but it's not used here to avoid a circular dependency - the weaver is run directly instead (using ant)

simple:
`mvn install`

build with tests:
`ant clean testjit test jar doc`

details:
* testjit runs the tests (after compiling) using the runtime weaver
* test runs the tests using the compile-time weaver, as well as some tests that don't require weaving
* doc generates sources.jar and javadoc.jar
* `mvn install:install-file -DpomFile=pom.xml -Dfile=target/kilim.jar -Dsources=target/sources.jar -Djavadoc=target/javadoc.jar`
* java 8 is the recommended platform, but should build (with ant), run and test under both 6 and 7
  * eg: `JAVA_HOME=path/to/java7 ant weave jar`


## Running

with runtime weaver:
```
ant clean compile

# using the runtime weaver
java -cp "target/classes:libs/*" kilim.tools.Kilim kilim.examples.PerfTest

# Userdata calls the trampoline (implicitly triggers the runtime weaver)
java -cp "target/classes:libs/*" kilim.examples.Userdata

# run the compile-time weaver
ant weave

# run woven code
java -cp "target/classes:libs/*" kilim.examples.PerfTest
```



## Overview

* java.lang.Runnable:   once a java.lang.Task starts running, it takes over the thread until it returns

* kilim.Continuation:  like java.lang.Runnable, but can yield (relinquish control of the thread) while preserving state. It’s caller must arrange to resume it at some appropriate time. Used for event-driven state machines where the event loop knows when to resume. The programmer is in charge of the event loop.

* kilim.Task:  like kilim.Continuation, but specifies a reason that it yields, and a scheduler automatically resumes it when the reason is no longer true. Typically used for a system of communicating state machines that communicate using Mailboxes. Releases the programmer from the job of mapping fibers to thread and of managing an event loop and the delivery of events.

* kilim.Pausable: a hypothetical exception used to declare intent. a method that declares this to be thrown will trigger the weaver

* kilim.Mailbox: a queue with Pausable methods that cause the calling Task method to yield when an operation would otherwise block and to resume automatically when the Mailbox because available for reading or writing

* kilim.Fiber: the structure that stores the Continuation (and Task) state


## Support

public support:
* [the mailing list](https://groups.google.com/forum/#!forum/kilimthreads)
* [github issues](https://github.com/nqzero/kilim/issues)



nqzero is currently the primary maintainer.
he's primarily interested in making Kilim easy to use and reliable and is actively using Kilim

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
* Copyright (c) 2014 Avinash Lakshman (hedviginc.com)
* Copyright (c) 2013 Nilang Shah
* Copyright (c) 2013 Jason Pell


This software is released under an MIT-style license (please see the
License file). Unless otherwise noted, all files in this distribution are
offered under these terms, and files that explicitly refer to the "MIT License"
refer to this license


**Important Exception - Caveat Emptor**

The hedvig contributions that added new files do not explicitly list a license or copyright.
I believe that copyright is implied so i've added the authors to each of those files.
I've contacted them and received what I understand to be confirmation that the project license applies,
but am waiting on a clearer statement.
As such, I'm unable to say with certainty if the license applies to those files:

* AffineThreadPool - used by the Scheduler
* KilimScheduledExecutor
* KilimStats.java
* ThreadFactoryImpl






