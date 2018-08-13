package kilim.demo;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import kilim.JettyHandler.Java7Handler;
import kilim.Pausable;
import kilim.Task;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;


/*
    kilim uses provided-scope for it's jetty support using jetty 9.3, which is java 8+ only
    this example project shows that kilim can be used with an older jetty with java 7
*/


/** example of using kilim's JettyHandler and jetty on java 7 - it works, though less elegant without lambdas */
public class KilimHandler7 {
    
    public static void main(String[] args) throws Exception {
        Server server = new Server(9104);
        MyHandler7 handler = new MyHandler7();
        server.setHandler(new kilim.JettyHandler(handler));
        server.start();
    }

    public static class MyHandler7 extends Java7Handler {
        public String handle(String target,Request br,HttpServletRequest req,HttpServletResponse resp) throws Pausable,Exception {
            Task.sleep(1000);
            return "cruel world = " + req.getPathInfo();
        }
    }
}
