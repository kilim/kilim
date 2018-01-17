package kilim;

import java.io.IOException;
import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;



public class JettyHandler extends AbstractHandler {
    Iface handler;
    public JettyHandler(Iface handler) { this.handler = handler; }

    public void handle(final String target,final Request br,final HttpServletRequest req,final HttpServletResponse resp) throws IOException, ServletException {
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

    
}
