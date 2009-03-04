package kilim.test.ex;
import kilim.Pausable;
public class ExFlow {
    void loop() throws Pausable {
        ExA a = null;
        int i;
        for (i = 0; i < 10; i++) {
            if (i < 5) {
                a = new ExC();
            } else {
                a = new ExD();;
            }
        }
        // at join, the stack must have types of [I,Lkilim.test.ex.ExFlow; and Lkilim.test.ex.ExA;
        // local vars-> 0:Lkilim.test.ex.ExFlow; 1:Lkilim.test.ex.ExA; 2:int 3:UNDEFINED 
        int x = 10 * join(a);
        System.out.println(i);
        System.out.println(x);
    }
    
    int join(ExA a) throws Pausable {  return 10;}
}