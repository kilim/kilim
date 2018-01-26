for the most part, kilim manages to weave arbitrary java code.
this document attempts to enumerate the known limitations


### java 7 interface method annotations

in java 7, annotations of interface methods are not preserved.
https://github.com/kilim/kilim/issues/53

this likely also affects class methods.
the root cause is that the method is woven, the original is fiber-enabled
and the original is replaced with a placeholder

since java 7 is EOL, this may not be fixed.
if you have a use case, add it to the bug report

as a work-around, the annotation is applied to the fiber-enabled method, so check that method instead of the original

### constructors can not be pausable

constructors must immediately call their super, so if a constructor yielded,
the weaver would not be able to skip the super call on the subsequent execution

we aren't aware of any means of working around this limitation, so it's highly
unlikely that this limitation will be relaxed


