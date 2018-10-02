#! /bin/bash

# requires a patch from git, ie the tag java10


dir=$(dirname "$0")
cd "$dir"

    rsync -a --delete ../src/ test
    git show -p java10 -- ":(top)test" | patch -p1


    JAVA_HOME=$java10 ../testing.sh pom10.xml

    
