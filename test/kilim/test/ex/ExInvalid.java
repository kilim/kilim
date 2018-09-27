package kilim.test.ex;

import kilim.Pausable;
import kilim.Task;

// Just a ununsed public class to make it easy to have a bunch of related test
// classes in one file
public class ExInvalid {
}

class ExInvalidConstructor {
    ExInvalidConstructor() throws Pausable {}
}
class ExInvalidConstructor2 {
    ExInvalidConstructor2() throws Exception {
        Task.sleep(1000);
    }
}
class ExInvalidConstructor3 {
    ExInvalidConstructor3() throws Pausable {
        Task.sleep(1000);
    }
}
class ExInvalidStaticBlock {
    static void foo() throws Pausable {}
    static {
        try { foo(); }
        catch (Exception ex) {}
    }
}
class ExInvalidCallP_NP {
    void foo() throws Pausable {}
    void bar() throws Exception {
        foo();
    }
}

// illegal to override a non-pausable method with a pausable one
class ExNPSuper {
    void foo() throws Exception {}
}
class ExInvalidPDerived extends ExNPSuper {
    void foo() throws Pausable {}
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
interface ExInvalidNPFace extends ExPFoo {
    void foo();
}
class ExInvalidNPImp implements ExPFoo {
    public void foo() {
    }
}

//------------------------------------------------
//Illegal to override a non-pausable interface method with a pausable one
interface ExNPFoo {
    void foo() throws Exception;
}
class ExInvalidPImp implements ExNPFoo {
    public void foo() throws Pausable {}
}
interface ExInvalidPFace extends ExNPFoo {
    void foo() throws Pausable;
}
