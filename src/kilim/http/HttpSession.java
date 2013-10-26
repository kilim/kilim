package kilim.http;

/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

import java.io.IOException;
import java.io.OutputStream;

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

}
