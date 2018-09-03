package kilim.http;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import kilim.Pausable;

/**
 * a minimal mvc framework for kilim, not available in java 7 or earlier
 */
public class KilimMvc {
    static String sep = "/";
    static String qsep = "\\?";
    static String wildcard = "{";
    static String asterisk = "*";
    ArrayList<Route> route = new ArrayList();
    public Route fallback;

    
    public static class Route {
        String method;
        String [] parts;
        boolean varquer;
        String [] queries = new String[0];
        Routeable handler;
        Scannable<? extends Router> source;
        Preppable prep;
        String uri;
        boolean skip;
        
        Route(String $uri,Routeable $handler) {
            uri = $uri;
            String [] pieces = uri.split(qsep,2);
            parts = pieces[0].split(sep);
            if (pieces.length > 1) {
                String [] qo = queries = pieces[1].split(sep);
                if (varquer = qo[qo.length-1].equals(asterisk))
                    queries = java.util.Arrays.copyOf(qo,qo.length-1);
            }
            handler = $handler;
            for (int ii=1; ii < parts.length; ii++)
                if (parts[ii].startsWith(wildcard)) parts[ii] = wildcard;
        }
        Route(String $method,String $uri,Routeable $handler) {
            this($uri,$handler);
            method = $method;
        }
        public Route(String $method,String $uri) {
            this($method,$uri,null);
            method = $method;
        }
        public Route() { skip = true; }
        /** for debugging only */
        boolean test(HttpRequest req) {
            Route.Info info = new Route.Info(req);
            return test(info,req);
        }
        boolean test(Info info,HttpRequest req) {
            if (info.parts.length != parts.length)
                return false;
            if (method != null && ! method.equals(req.method))
                return false;
            int num = 0;
            for (int ii=0; ii < parts.length; ii++)
                if (parts[ii]==wildcard)
                    info.keys[num++] = info.parts[ii];
                else if (! parts[ii].equals(info.parts[ii]))
                    return false;
            
            if (varquer==false & info.queries.count != queries.length)
                return false;
            for (String query : queries)
                if ((info.keys[num++] = info.get(query)) == null)
                    return false;
            return true;
        }
        Route set(Factory factory) {
            handler = factory;
            return this;
        }
        Route skip() { skip = true; return this; }
        public static class Info {
            String [] parts;
            String [] keys;
            KeyValues queries;
            String get(String query) {
                // queries.get conflates a missing key with a missing value, ie both are ""
                int index = queries.indexOf(query);
                return index < 0 ? null : queries.values[index];
            }
            Info(HttpRequest req) {
                parts = req.uriPath.split(sep);
                queries = req.getQueryComponents();
                keys = new String[parts.length + queries.keys.length];
            }
        }
    }

    // fixme:kilim - overriding a default method appears to cause kilim to weave incorrectly
    public interface Routeable {};
    public interface Routeable0 extends Routeable { Object accept() throws Pausable,Exception; }
    public interface Routeable1 extends Routeable { Object accept(String s1) throws Pausable,Exception; }
    public interface Routeable2 extends Routeable { Object accept(String s1,String s2) throws Pausable,Exception; }
    public interface Routeable3 extends Routeable { Object accept(String s1,String s2,String s3) throws Pausable,Exception; }
    public interface Routeable4 extends Routeable { Object accept(String s1,String s2,String s3,String s4) throws Pausable,Exception; }
    public interface Routeable5 extends Routeable { Object accept(String s1,String s2,String s3,String s4,String s5) throws Pausable,Exception; }
    public interface Routeablex extends Routeable { Object accept(String [] keys) throws Pausable,Exception; }
    public interface Fullable0  extends Routeable { Object accept(HttpRequest req,HttpResponse resp) throws Pausable,Exception; }
    public interface Factory<TT extends Routeable,PP extends Router> extends Routeable { TT make(PP pp); }

    void checkRoute(Route r2) {
        int limit = 10;
        Routeable rr = r2.handler;
        for (int ii=0; rr instanceof Factory; ii++) {
            if (ii > limit)
                throw new RuntimeException("route factory recursion limit exceeded: "+r2);
            Router pp = supply(r2.source,null);
            pp.init(null,null,null);
            rr = ((Factory) rr).make(pp);
        }
        boolean known =
                rr instanceof Routeable0 |
                rr instanceof Routeable1 |
                rr instanceof Routeable2 |
                rr instanceof Routeable3 |
                rr instanceof Routeable4 |
                rr instanceof Routeable5 |
                rr instanceof Routeablex;
        if (!known)
            throw new RuntimeException("no known routing available: "+r2);
    }
    
    public Object route(Session session,HttpRequest req,HttpResponse resp) throws Pausable,Exception {
        Route.Info info = new Route.Info(req);
        for (int ii=0; ii < route.size(); ii++) {
            Route r2 = route.get(ii);
            if (r2.test(info,req))
                return route(null,session,r2,r2.handler,info.keys,req,resp);
        }
        return route(null,session,fallback,fallback.handler,info.keys,req,resp);
    }
    Object route(Routeable hh,String [] keys) throws Pausable,Exception {
        if (hh instanceof Routeable0) return ((Routeable0) hh).accept();
        if (hh instanceof Routeable1) return ((Routeable1) hh).accept(keys[0]);
        if (hh instanceof Routeable2) return ((Routeable2) hh).accept(keys[0],keys[1]);
        if (hh instanceof Routeable3) return ((Routeable3) hh).accept(keys[0],keys[1],keys[2]);
        if (hh instanceof Routeable4) return ((Routeable4) hh).accept(keys[0],keys[1],keys[2],keys[3]);
        if (hh instanceof Routeable5) return ((Routeable5) hh).accept(keys[0],keys[1],keys[2],keys[3],keys[4]);
        if (hh instanceof Routeablex) return ((Routeablex) hh).accept(keys);
        throw new RuntimeException("Routable is not directly implementable - implement a sub-interface instead");
    }
    Object route(Router pp,Session session,Route r2,Routeable hh,String [] keys,HttpRequest req,HttpResponse resp) throws Pausable,Exception {
        // fixme - should a factory that produces a factory supply a second pp ???
        // this case is currently unused
        // otherwise, pp is null iff hh is a factory
        if (pp==null)
            pp = supply(r2.source,null);
        if (hh instanceof Factory) {
            pp.init(session,req,resp);
            if (r2.prep != null)
                r2.prep.accept(pp);
            Routeable h2 = ((Factory) hh).make(pp);
            return route(pp,session,r2,h2,keys,req,resp);
        }
        return route(hh,keys);
    }

    // unused but useful for debugging routing problems
    /**
     * filter the registered routes that match the request
     * @param req the request to test the routes against
     * @return the indices of the matching routes
     */
    ArrayList<Integer> filterRoutes(HttpRequest req) {
        Route.Info info = new Route.Info(req);
        ArrayList<Integer> keys = new ArrayList();
        for (int ii=0; ii < route.size(); ii++)
            if (route.get(ii).test(info,req)) keys.add(ii);
        return keys;
    }
    
    public interface Preppable<PP> { void accept(PP val) throws Pausable; }
    public interface Scannable<PP extends Router> { PP supply(Clerk router); }

    protected <PP extends Router> PP supply(Scannable<PP> source,Clerk router) {
        PP pp = source.supply(router);
        return pp;
    }
    
    public interface Clerk {
        public void accept(Route router);
    }
    
    private class LocalConsumer implements Clerk {
        ArrayList<Route> local;
        LocalConsumer(ArrayList<Route> local) { this.local = local; }
        public void accept(Route router) { local.add(router); }
    }

    private class LocalScanner<PP extends Router> implements Scannable<PP> {
        PP pp;
        public LocalScanner(PP pp) { this.pp = pp; }
        public PP supply(Clerk sink) { return pp; }
    }

    public <PP extends Router> PP scan(Scannable<PP> source,Preppable<PP> auth) {
        ArrayList<Route> local = new ArrayList();
        PP pp = supply(source,new LocalConsumer(local));
        for (Route rr : local)
            addRoute(rr,new LocalScanner(pp),source,auth);
        return pp;
    }

    <PP extends Router> void addRoute(Route rr,Scannable<PP> direct,Scannable<PP> source,Preppable<PP> auth) {
        if (rr.handler instanceof Factory) {
            rr.source = source;
            rr.prep = (Preppable<Router>) auth;
        }
        else
            rr.source = direct;
        checkRoute(rr);
        if (rr.skip)
            fallback = rr;
        else
            route.add(rr);
    }
    
    public static class Router<PP extends Router> {
        boolean first;
        private Clerk mk;
        public Session session;
        public HttpRequest req;
        public HttpResponse resp;

        public Router(Clerk mk) {
            this.mk = mk;
            first = mk != null;
        }
        public void init(Session $session,HttpRequest $req,HttpResponse $resp) {
            session = $session;
            req = $req;
            resp = $resp;
        }
        void add(Route rr) {
            if (first)
                mk.accept(rr);
        }

        public static Route mapping(String method,String uri) { return new Route(method,uri); }
        
        public void add(String uri,Routeable0 rr) { add(new Route(uri,rr)); }
        public void add(String uri,Routeable1 rr) { add(new Route(uri,rr)); }
        public void add(String uri,Routeable2 rr) { add(new Route(uri,rr)); }
        public void add(String uri,Routeable3 rr) { add(new Route(uri,rr)); }
        public void add(String uri,Routeable4 rr) { add(new Route(uri,rr)); }
        public void add(String uri,Routeable5 rr) { add(new Route(uri,rr)); }

        public void make0(String uri,Factory<Routeable0,PP> ff) { add(new Route(uri,ff)); }
        public void make1(String uri,Factory<Routeable1,PP> ff) { add(new Route(uri,ff)); }
        public void make2(String uri,Factory<Routeable2,PP> ff) { add(new Route(uri,ff)); }
        public void make3(String uri,Factory<Routeable3,PP> ff) { add(new Route(uri,ff)); }
        public void make4(String uri,Factory<Routeable4,PP> ff) { add(new Route(uri,ff)); }
        public void make5(String uri,Factory<Routeable5,PP> ff) { add(new Route(uri,ff)); }

        public void make0(Route route,Factory<Routeable0,PP> ff) { add(route.set(ff)); }
        public void make1(Route route,Factory<Routeable1,PP> ff) { add(route.set(ff)); }
        public void make2(Route route,Factory<Routeable2,PP> ff) { add(route.set(ff)); }
        public void make3(Route route,Factory<Routeable3,PP> ff) { add(route.set(ff)); }
        public void make4(Route route,Factory<Routeable4,PP> ff) { add(route.set(ff)); }
        public void make5(Route route,Factory<Routeable5,PP> ff) { add(route.set(ff)); }

    }
    
    public static void sendJson(HttpResponse resp,byte [] msg) throws IOException {
        // fixme -- this appears to block for long messages
        resp.setContentType("application/json");
        resp.getOutputStream().write(msg);
    }
    public interface KilimHandler {
        public void handle(Session session,HttpRequest req,HttpResponse resp) throws Pausable, Exception;
    }
    public static class Session extends HttpSession {
        KilimHandler handler;
        public Session(KilimHandler handler) { this.handler = handler; }
        protected Session() {}
        public void handle(HttpRequest req,HttpResponse resp) throws Pausable, Exception {
            handler.handle(this,req,resp);
        }
        public void execute() throws Pausable, Exception {
            try {
                HttpRequest req = new HttpRequest();
                HttpResponse resp = new HttpResponse();
                while (true) {
                    super.readRequest(req);
                    if (req.keepAlive())
                        resp.addField("Connection", "Keep-Alive");

                    handle(req,resp);

                    if (!req.keepAlive()) 
                        break;
                    else
                        nop();
                }
            }
            catch (EOFException e) {}
            catch (IOException ioe) {}
            super.close();
        }
        public void send(HttpResponse resp,byte [] msg,String type) throws IOException, Pausable {
            // fixme -- this appears to block for long messages
            resp.setContentType(type);
            resp.getOutputStream().write(msg);
            sendResponse(resp);
        }
    }

    private static void nop() {}

}
