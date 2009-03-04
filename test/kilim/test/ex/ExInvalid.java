package kilim.test.ex;

import kilim.Pausable;

// Just a ununsed public class to make it easy to have a bunch of related test
// classes in one file
public class ExInvalid {
}


// illegal to override a non-pausable method with a pausable one
class ExNPSuper {
    void foo() {}
}

//illegal to override a pausable method with a non-pausable one
class ExPSuper {
    void foo() throws Pausable {}
}
class ExInvalidNPDerived extends ExPSuper {
    void foo() {
        
    }   
}


//------------------------------------------------
// Illegal to override an pausable interface method with a non-pausable one
interface ExPFoo {
    void foo() throws Pausable;
}
interface ExNPBar extends ExPFoo {}
class ExInvalidNPImp implements ExNPBar {
    public void foo() {
    }
}

//------------------------------------------------
//Illegal to override a non-pausable interface method with a pausable one
interface ExNPFoo {
    void foo();
}

interface ExNPBaz extends ExNPFoo {
}


