#!/bin/bash
echo Cleaning
rm -rf testclasses-* classes-* testclasses classes
echo Build via Ant
ant clean compile
mv classes classes-ant
mv testclasses testclasses-ant
echo Build via shell script
./build.sh
mv classes classes-sh
mv testclasses testclasses-sh
echo Build via Maven
mvn install
mv classes classes-mvn
mv testclasses testclasses-mvn
echo Comparing
diff -aqr classes-mvn/ classes-sh/ && echo success
diff -aqr testclasses-mvn/ testclasses-sh/ && echo success
diff -aqr classes-ant/ classes-sh/ && echo success
diff -aqr testclasses-ant/ testclasses-sh/ && echo success