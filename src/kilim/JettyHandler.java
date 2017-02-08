package kilim;

import java.io.IOException;
import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;



public class JettyHandler extends AbstractHandler {
    Iface handler;
    public JettyHandler(Iface handler) { this.handler = handler; }

    public void handle(String target,Request br,HttpServletRequest req,HttpServletResponse resp) throws IOException, ServletException {
        final AsyncContext async = req.startAsync();
        new kilim.Task() {
            public void execute() throws Pausable, Exception {
                try {
                    String result = handler.handle(target,br,req,resp);
                    if (result != null) resp.getOutputStream().print(result);
                }
                catch (Exception ex) { resp.sendError(500,"the server encountered an error"); }
                br.setHandled(true);
                async.complete();
            }
        }.start();
    }

    
    public interface Iface {
        String handle(String target,Request br,HttpServletRequest req,HttpServletResponse resp) throws Pausable, Exception;
    }

    // NB: jetty server is scope:provided, so this class won't run without placing jetty on the classpath
    public static void main(String[] args) throws Exception {
        Server server = new Server(9104);

        // for java8, use a lambda instead of the new Iface():
        //   (target,raw,req,resp) -> { return "cruel world"; }
        server.setHandler(new JettyHandler(new Iface() {
            public String handle(String target,Request br,HttpServletRequest req,HttpServletResponse resp)
                    throws Pausable {
                return "cruel world";
            }
        }));
        server.start();
    }
    
}
