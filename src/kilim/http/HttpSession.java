package kilim.http;

/* Copyright (c) 2006, Sriram Srinivasan, nqzero 2016
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

import kilim.Pausable;
import kilim.nio.SessionTask;

/**
 * Responsible for creating an HTTPRequest object out of raw bytes from a socket, and for sending an HTTPResponse object
 * in its entirety.
 */
public class HttpSession extends SessionTask {

    /**
     * Reads the socket, parses the HTTP headers and the body (including chunks) into the req object.
     * 
     * @param req
     *            . The HttpRequest object is reset before filling it in.
     * @return the supplied request object. This is to encourage buffer reuse.
     * @throws IOException
     */
    public HttpRequest readRequest(HttpRequest req) throws IOException, Pausable {
        req.reuse();
        req.readFrom(endpoint);
        return req;
    }

    // public static void dumpBuf(String msg, ByteBuffer buffer) {
    // System.out.println(msg);
    // int pos = buffer.position();
    // for (int i = 0; i < pos; i++) {
    // System.out.print((char)buffer.get(i));
    // }
    // System.out.println("============================");
    // }

    /**
     * Send the response object in its entirety, and mark it for reuse. Often, the resp object may only contain the
     * header, and the body is sent separately. It is the caller's responsibility to make sure that the body matches the
     * header (in terms of encoding, length, chunking etc.)
     */
    public void sendResponse(HttpResponse resp) throws IOException, Pausable {
        resp.writeTo(endpoint);
        resp.reuse();
    }

    static byte[] pre  = "<html><body><p>".getBytes();
    static byte[] post = "</body></html>".getBytes();

    /**
     * Send an error page to the client.
     * 
     * @param resp The response object. 
     * @param statusCode See HttpResponse.ST*  
     * @param htmlMsg  The body of the message that gives more detail. 
     * @throws IOException
     * @throws Pausable
     */
    public void problem(HttpResponse resp, byte[] statusCode, String htmlMsg) throws IOException, Pausable {
        resp.status = statusCode;
        resp.setContentType("text/html");
        OutputStream os = resp.getOutputStream();
        os.write(pre);
        os.write(htmlMsg.getBytes());
        os.write(post);
        sendResponse(resp);
    }

    /**
     * send a file with content length to the client
     * @param req the request
     * @param resp the response
     * @param file the file to send
     * @param contentType if non-null, set the content type
     * @return 0 on success, 1 for not found, 2 for couldn't send
     */
    public int sendFile(HttpRequest req,HttpResponse resp,File file,String contentType)
            throws Pausable {
        FileInputStream fis = null;
        FileChannel fc;
        boolean headOnly = req.method.equals("HEAD");

        try {
            fis = new FileInputStream(file);
            fc = fis.getChannel();
        } catch (IOException ex) {
            try { fis.close(); } catch (Exception ex2) {}
            return 1;
        }
        try {
            if (contentType != null)
                resp.setContentType(contentType);
            resp.setContentLength(file.length());
            sendResponse(resp);
            if (!headOnly)
                endpoint.write(fc, 0, file.length());
        }
        catch (IOException ex) {
            return 2;
        }
        finally {
            try { fc.close(); } catch (Exception ex) {}
            try { fis.close(); } catch (Exception ex) {}
        }
        return 0;
    }
    public boolean check(HttpResponse resp,File file,String...bases) throws IOException, Pausable {
        try {
            String path = file.getCanonicalPath();
            for (String base : bases)
                if (path.startsWith(base))
                    return true;
        }
        catch (Exception ex) {}
        return false;
    }

    public interface StringRouter {
        public String route(HttpRequest req) throws Pausable;
    }
    public static class StringSession extends HttpSession {
        StringRouter handler;
        public StringSession(StringRouter handler) { this.handler = handler; }
        public void execute() throws Pausable, Exception {
            try {
                // We will reuse the req and resp objects
                HttpRequest req = new HttpRequest();
                HttpResponse resp = new HttpResponse();
                while (true) {
                    super.readRequest(req);
                    if (req.keepAlive())
                        resp.addField("Connection", "Keep-Alive");
                    OutputStream out = resp.getOutputStream();
                    String result = handler.route(req);
                    out.write( result.getBytes() );
                    sendResponse(resp);
                    if (!req.keepAlive()) 
                        break;
                }
            }
            catch (EOFException e) {}
            catch (IOException ioe) {
                System.out.println("[" + this.id + "] IO Exception:" + ioe.getMessage());
            }
            catch (Exception ex) {
                System.out.println("HttpSession.Simple:exception -- " + ex);
                ex.printStackTrace();
            }
            super.close();
        }
    }
}
