package kilim.examples;

import java.lang.reflect.Method;

import kilim.Pausable;
import kilim.Task;

public class Reflect extends Task {
  @Override
  public void execute() throws Pausable, Exception {
    int n = test();
    System.out.println("test (normal invocation of instance method): " + n);
    // Invoking test() via reflection
    Method mthd = Reflect.class.getDeclaredMethod("test", new Class[0]);
    Object ret = Task.invoke(mthd, this, /* no args */(Object[])null);
    System.out.println("test (reflect invocation of instance method): " + ret);

    n = static_test();
    System.out.println("test (normal invocation of static method): " + n);
    // Invoking test() via reflection
    mthd = Reflect.class.getDeclaredMethod("static_test", new Class[0]);
    ret = Task.invoke(mthd, /* target = */ null, /* no args */(Object[])null);
    System.out.println("test (reflect invocation of static method): " + ret);
    System.exit(0);
  }
  
  public int test() throws Pausable {
    int m = 10;
    for (int i = 0; i < 2; i++) { // Force multiple yields
      Task.sleep(100); 
      m *= 2;
    }
    return m; // must return 40 if all goes well.
  }

  public static int static_test() throws Pausable {
    int m = 10;
    for (int i = 0; i < 2; i++) { // Force multiple yields
      Task.sleep(100); 
      m *= 2;
    }
    return m; // must return 40 if all goes well.
  }

  
  public static void main(String args[]) {
    Reflect ref = new Reflect();
    ref.start();
    ref.joinb();
  }
}
