/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import junit.framework.TestCase;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.http.HttpRequest;
import kilim.http.HttpResponse;
import kilim.http.HttpSession;
import kilim.nio.NioSelectorScheduler;

public class TestHTTP extends TestCase {
    static int PORT = 9797;
    static final int ITERS = 10;
    static final int NCLIENTS = 100;
    NioSelectorScheduler nio;
    
    @Override
    protected void setUp() throws Exception {
        nio = new NioSelectorScheduler(); // Starts a single thread that manages the select loop
        nio.listen(PORT, TestHttpServer.class, Scheduler.getDefaultScheduler()); //
        Thread.sleep(50); // Allow the socket to be registered and opened.
    }
    
    @Override
    protected void tearDown() throws Exception {
        nio.shutdown();
        Scheduler.getDefaultScheduler().shutdown();
        PORT++; // start the next test with a new socket.
    }
    
    public void testReqResp() throws IOException {
        String path = "/hello";
        URL url = new URL("http://localhost:" + PORT + path);
        URLConnection conn = url.openConnection();
        conn.setDefaultUseCaches(false);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(
                conn.getInputStream()));
        String s = in.readLine();
        assertTrue(s.contains(path));
        in.close();
    }
    
    public void testQuery() throws IOException {
        String path = "/%7ekilim/home.html?info?code=200&desc=Rolls%20Royce";
        URL url = new URL("http://localhost:" + PORT + path);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setDefaultUseCaches(false);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(
                conn.getInputStream()));
        String s = in.readLine();
        assertTrue(s.contains("~kilim"));
        assertTrue(s.contains("desc:Rolls Royce"));
        in.close();
        
    }
    
    public void testChunking() throws IOException {
        String path = "/%7ekilim/home.html?buy?code=200&desc=Rolls%20Royce";
        URL url = new URL("http://localhost:" + PORT + path);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setDefaultUseCaches(false);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setChunkedStreamingMode(17); 
        StringBuilder sb = new StringBuilder(268);
        sb.append("BEGIN");
        for (int i = 0; i < 10; i++) {
            sb.append("abcdefghijklmnopqrstuvwxyz");
        }
        sb.append("END");

        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                    conn.getOutputStream()));
        
        out.write(sb.toString());
        out.flush();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(
                conn.getInputStream()));
        String s = in.readLine();
        assertEquals(s.length(), sb.length());
        assertTrue(s.startsWith("BEGIN"));
        assertTrue(s.endsWith("END"));
        
        in.close();
        out.close();
        
    }
    
    public static class TestHttpServer extends HttpSession {
        public void execute() throws Pausable, Exception {
            try {
                while (true) {
                    HttpRequest req = new HttpRequest();
                    HttpResponse resp = new HttpResponse();
                    // Fill up the request object. This pauses until the entire request has
                    // been read in, including all chunks.
                    super.readRequest(req);
                    // System.out.println(req);
                    if (req.method.equals("GET")) {
                        resp.setContentType("text");
                        PrintWriter pw = new PrintWriter(resp.getOutputStream());
                        pw.append(req.uriPath).append(req.getQueryComponents().toString());
                        pw.flush();
                        sendResponse(resp);
                    } else if (req.method.equals("POST")) {
                        resp.setContentType("text");
                        PrintWriter pw = new PrintWriter(resp.getOutputStream());
                        String s = req.extractRange(req.contentOffset, req.contentOffset + req.contentLength);
                        pw.append(s);
                        pw.flush();
                        sendResponse(resp);
                    } else {
                        problem(resp, HttpResponse.ST_BAD_REQUEST, "Only get accepted");
                    }
                    if (!req.keepAlive()) {
                        break;
                    }
                }
            } catch (EOFException ignore) {
            }
        }
    }
}
