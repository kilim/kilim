package kilim.demo;

import java.io.IOException;
import java.nio.ByteBuffer;
import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import kilim.Pausable;
import kilim.Task;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.http.PreEncodedHttpField;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.BufferUtil;



public class KilimServlet extends HttpServlet {
    ByteBuffer helloWorld = BufferUtil.toBuffer("hello world");
    HttpField contentType = new PreEncodedHttpField(HttpHeader.CONTENT_TYPE,MimeTypes.Type.TEXT_PLAIN.asString());
    private static int delay = 1000;

    void reply(AsyncContext async) {
        try {
            Request br = (Request) async.getRequest();
            br.setHandled(true);
            br.getResponse().getHttpFields().add(contentType); 
            br.getResponse().getHttpOutput().sendContent(helloWorld.slice());
            async.complete();
        } catch (IOException ex) {}
    }

    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final AsyncContext async = req.startAsync();
        async.setTimeout(30000);
        new kilim.Task() {
            public void execute() throws Pausable, Exception {
                if (delay > 0) Task.sleep(delay);
                reply(async);
            }
        }.start();
    }



    public static void main(String[] args) throws Exception {
        if (args.length > 0) delay = Integer.valueOf(args[0]);
        Server server = new Server(9099);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        ServletHolder holder = new ServletHolder(new KilimServlet());
        holder.setAsyncSupported(true);
        context.addServlet(holder,"/hello");
        server.setHandler(context);
        server.start();
    }
}
