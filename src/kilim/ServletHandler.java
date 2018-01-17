package kilim;

import java.io.IOException;
import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



public class ServletHandler extends HttpServlet {
    Iface handler;
    public ServletHandler(Iface handler) { this.handler = handler; }

    protected void service(final HttpServletRequest req,final HttpServletResponse resp) throws ServletException,IOException {
        final AsyncContext async = req.startAsync();
        new kilim.Task() {
            public void execute() throws Pausable, Exception {
                try {
                    String result = handler.handle(req,resp);
                    if (result != null) resp.getOutputStream().print(result);
                }
                catch (Exception ex) { resp.sendError(500,"the server encountered an error"); }
                async.complete();
            }
        }.start();
    }

    
    public interface Iface {
        String handle(HttpServletRequest req,HttpServletResponse resp) throws Pausable, Exception;
    }

    
}
