package kilim.demo;

import org.eclipse.jetty.server.Server;



public class KilimHandler {
    
    public static void main(String[] args) throws Exception {
        Server server = new Server(9104);
        server.setHandler(new kilim.JettyHandler((target,raw,req,resp) -> {
            return "cruel world";
        }));
        server.start();
    }
    
}
