echo "Testing Kilim Weaver"
java -ea -cp ./classes:./libs/asm-all-5.0.3.jar:./libs/junit.jar junit.textui.TestRunner kilim.test.AllNotWoven

echo "Task, mailbox tests"
java -ea -Dkilim.Scheduler.numThreads=10 -cp ./testclasses:./classes:./libs/asm-all-5.0.3.jar:./libs/junit.jar junit.textui.TestRunner kilim.test.AllWoven
