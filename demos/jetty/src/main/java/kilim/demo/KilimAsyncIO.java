package kilim.demo;

import java.io.IOException;
import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;



// note
// servlet 3.1 introduced this non-blocking i/o (nbio)
// it's not immediately clear which of the servlet methods are invalid when utilizing it
// at one point, i read a document that defined these limitations
//   best guess: jetty source code
// based on that reading, my conclusion was that most of the servlet methods work normally
// and it's only a couple of methods that parse the body that are unavailable with nbio


public class KilimAsyncIO extends AbstractHandler implements ReadListener {
    Iface handler;
    private ServletInputStream in;
    private byte[] buffer = new byte[1024];
    public KilimAsyncIO(Iface handler) { this.handler = handler; }
    Mailbox<String> box = new Mailbox<String>();

    AsyncContext async;    
    
    public void handle(String target,Request br,HttpServletRequest req,HttpServletResponse resp) throws IOException, ServletException {
        async = req.startAsync();
        in = req.getInputStream();
        in.setReadListener(this);
        new kilim.Task() {
            public void execute() throws Pausable, Exception {
                box.get();
                Task.sleep(1000);
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
    
    public static void main(String[] args) throws Exception {
        Server server = new Server(9104);
        server.setHandler(new KilimAsyncIO((target,raw,req,resp) -> {
            return "cruel world";
        }));
        server.start();
    }

    
    public void onDataAvailable() throws IOException {
        while (in.isReady()) {
            int len = in.read(buffer);
            if (len>0) {}
            if (in.isFinished()) { break; }
        }
    }

    public void onAllDataRead() throws IOException {
        box.putb("hello");
    }

    public void onError(Throwable t) {
    }
    
}
