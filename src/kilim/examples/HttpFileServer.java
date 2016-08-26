/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.examples;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.util.HashMap;

import kilim.Pausable;
import kilim.http.HttpRequest;
import kilim.http.HttpResponse;
import kilim.http.HttpServer;
import kilim.http.HttpSession;

/**
 * A simple file server over http
 * 
 * Usage: Run java kilim.examples.HttpFileServer [base directory name] From a browser, go to "http://localhost:7262".
 * 
 * A HttpFileServer object is a SessionTask, and is thus a thin wrapper over the socket connection. Its execute() method
 * is called once on connection establishment. The HttpRequest and HttpResponse objects are wrappers over a bytebuffer,
 * and unrelated to the socket. The request object is "filled in" by HttpSession.readRequest() and the response object
 * is sent by HttpSession.sendResponse(). The rest of the code is related to the mechanics of file serving, common to
 * Kilim and non-Kilim approaches alike. The objective of this example is merely to demonstrate Kilim API, not to have a
 * fully functioning file server.
 */
public class HttpFileServer extends HttpSession {
    public static File   baseDirectory;
    public static String baseDirectoryName;

    public static void main(String[] args) throws IOException {
        baseDirectoryName = ".";
        if (args.length > 0) {
            baseDirectoryName = args[0];
        }
        baseDirectory = new File(baseDirectoryName);
        if (!baseDirectory.isDirectory()) {
            usage();
        }
        baseDirectoryName = baseDirectory.getCanonicalPath();

        // create a listener on port 7262. An instance of HttpFileServer is created upon
        // every new socket connection to this port.
        new HttpServer(7262, HttpFileServer.class);
        System.out.println("HttpFileServer listening on http://localhost:7262");
    }

    public static void usage() {
        System.err.println("Usage: java kilim.examples.HttpFileServer [<baseDirectory>]");
        System.exit(1);
    }

    @Override
    public void execute() throws Pausable, Exception {
        try {
            // We will reuse the req and resp objects
            HttpRequest req = new HttpRequest();
            HttpResponse resp = new HttpResponse();
            while (true) {
                // Fill up the request object. This pauses until the entire request has
                // been read in, including all chunks.
                super.readRequest(req);
                // System.out.println(req);
                if (req.method.equals("GET") || req.method.equals("HEAD")) {
                    File f = urlToPath(req);
                    System.out.println("[" + this.id + "] Read: " + f.getPath());
                    if (check(resp, f)) {
                        boolean headOnly = req.method.equals("HEAD");
                        if (f.isDirectory())
                            sendDirectory(resp, f, headOnly);
                        else
                            sendFile(resp, f, headOnly);
                    }
                } else {
                    super.problem(resp, HttpResponse.ST_FORBIDDEN, "Only GET and HEAD accepted");
                }
                if (!req.keepAlive()) {
                    break;
                }
            }
        } catch (EOFException e) {
            System.out.println("[" + this.id + "] Connection Terminated");
        } catch (IOException ioe) {
            System.out.println("[" + this.id + "] IO Exception:" + ioe.getMessage());
        }
        super.close();
    }

    private File urlToPath(HttpRequest req) {
        return (req.uriPath == null) ? baseDirectory : new File(baseDirectory, req.uriPath);
    }

    public boolean check(HttpResponse resp, File file) throws IOException, Pausable {
        byte[] status = HttpResponse.ST_OK;
        String msg = "";
        
        if (!file.exists()) {
            status = HttpResponse.ST_NOT_FOUND;
            msg = "File Not Found: " + file.getName();
        } else if (!file.canRead()) {
            status = HttpResponse.ST_FORBIDDEN;
            msg = "Unable to read file " + file.getName();
        } else {
            try {
                String path = file.getCanonicalPath();
                if (!path.startsWith(baseDirectoryName)) {
                    throw new SecurityException();
                }
            } catch (Exception e) {
                status = HttpResponse.ST_FORBIDDEN;
                msg = "Error retrieving " + file.getName() + ":<br>" + e.getMessage();
            }
        }
        if (status != HttpResponse.ST_OK) {
            problem(file, resp, status, msg);
            return false;
        } else {
            return true;
        }
    }

    public void sendFile(HttpResponse resp, File file, boolean headOnly) throws IOException, Pausable {
        FileInputStream fis;
        FileChannel fc;

        try {
            fis = new FileInputStream(file);
            fc = fis.getChannel();
        } catch (IOException ioe) {
            problem(file, resp, HttpResponse.ST_NOT_FOUND, "Send exception: " + ioe.getMessage());
            return;
        }
        try {
            String contentType = mimeType(file);
            if (contentType != null) {
                resp.setContentType(contentType);
            }
            resp.setContentLength(file.length());
            // Send the header first (with the content type and length)
            super.sendResponse(resp);
            // Send the contents; this uses sendfile or equivalent underneath.
            endpoint.write(fc, 0, file.length());
        } finally {
            fc.close();
            fis.close();
        }
    }

    public void sendDirectory(HttpResponse resp, File file, boolean headOnly) throws Pausable, IOException {
        PrintStream p = new PrintStream(resp.getOutputStream());
        String relDir = getRelPath(file);
        p.print("<html><head><title>Index of ");
        p.print(relDir);
        p.print("</title></head><body ");
        p.print("><h2>Index of ");
        p.print(relDir.equals(".") ? "/" : relDir);
        p.print("</h2>");
        String names[] = file.list();
        if (names == null) {
            p.print("No files found");
        } else {
            for (int i = 0; i < names.length; i++) {
                // <a href="webpath">name</a>
                p.print("<a href=\"");
                p.print(relDir);
                p.print('/');
                p.print(names[i]);
                p.print("\">");
                p.print(names[i]);
                p.print("</a><br>");
            }
        }
        p.print("</body></html>");
        p.flush();
        super.sendResponse(resp);
    }

    public void problem(File file, HttpResponse resp, byte[] statusCode, String msg) throws IOException, Pausable {
        System.out.println("[" + id + "]. Error retrieving " + file.getAbsolutePath() + "':\n   " + msg);
        super.problem(resp, statusCode, msg);
    }

    private String getRelPath(File file) throws IOException {
        String path = file.getCanonicalPath();
        if (!path.startsWith(baseDirectoryName)) {
            throw new SecurityException();
        }
        path =  path.substring(baseDirectoryName.length()); // include the "/"
        return (path.length() == 0) ? "." : path;
    }

    public static HashMap<String, String> mimeTypes = new HashMap<String, String>();
    
    static {
        mimeTypes.put("html", "text/html");
        mimeTypes.put("htm", "text/html");
        mimeTypes.put("txt", "text/plain");
        mimeTypes.put("xml", "text/xml");
        mimeTypes.put("css", "text/css");
        mimeTypes.put("sgml", "text/x-sgml");
        mimeTypes.put("sgm", "text/x-sgml");
        // Images
        mimeTypes.put("gif", "image/gif");
        mimeTypes.put("jpg", "image/jpeg");
        mimeTypes.put("jpeg", "image/jpeg");
        mimeTypes.put("png", "image/png");
        mimeTypes.put("bmp", "image/bmp");
        mimeTypes.put("tif", "image/tiff");
        mimeTypes.put("tiff", "image/tiff");
        mimeTypes.put("rgb", "image/x-rgb");
        mimeTypes.put("xpm", "image/x-xpixmap");
        mimeTypes.put("xbm", "image/x-xbitmap");
        mimeTypes.put("svg", "image/svg-xml ");
        mimeTypes.put("svgz", "image/svg-xml ");
        // Audio
        mimeTypes.put("au", "audio/basic");
        mimeTypes.put("snd", "audio/basic");
        mimeTypes.put("mid", "audio/mid");
        mimeTypes.put("midi", "audio/mid");
        mimeTypes.put("rmi", "audio/mid");
        mimeTypes.put("kar", "audio/mid");
        mimeTypes.put("mpga", "audio/mpeg");
        mimeTypes.put("mp2", "audio/mpeg");
        mimeTypes.put("mp3", "audio/mpeg");
        mimeTypes.put("wav", "audio/wav");
        mimeTypes.put("aiff", "audio/aiff");
        mimeTypes.put("aifc", "audio/aiff");
        mimeTypes.put("aif", "audio/x-aiff");
        mimeTypes.put("ra", "audio/x-realaudio");
        mimeTypes.put("rpm", "audio/x-pn-realaudio-plugin");
        mimeTypes.put("ram", "audio/x-pn-realaudio");
        mimeTypes.put("sd2", "audio/x-sd2");
        // Applications
        mimeTypes.put("bin", "application/octet-stream");
        mimeTypes.put("dms", "application/octet-stream");
        mimeTypes.put("lha", "application/octet-stream");
        mimeTypes.put("lzh", "application/octet-stream");
        mimeTypes.put("exe", "application/octet-stream");
        mimeTypes.put("dll", "application/octet-stream");
        mimeTypes.put("class", "application/octet-stream");
        mimeTypes.put("hqx", "application/mac-binhex40");
        mimeTypes.put("ps", "application/postscript");
        mimeTypes.put("eps", "application/postscript");
        mimeTypes.put("pdf", "application/pdf");
        mimeTypes.put("rtf", "application/rtf");
        mimeTypes.put("doc", "application/msword");
        mimeTypes.put("ppt", "application/powerpoint");
        mimeTypes.put("fif", "application/fractals");
        mimeTypes.put("p7c", "application/pkcs7-mime");
        // Application/x
        mimeTypes.put("js", "application/x-javascript");
        mimeTypes.put("z", "application/x-compress");
        mimeTypes.put("gz", "application/x-gzip");
        mimeTypes.put("tar", "application/x-tar");
        mimeTypes.put("tgz", "application/x-compressed");
        mimeTypes.put("zip", "application/x-zip-compressed");
        mimeTypes.put("dvi", "application/x-dvi");
        mimeTypes.put("tex", "application/x-tex");
        mimeTypes.put("latex", "application/x-latex");
        mimeTypes.put("tcl", "application/x-tcl");
        mimeTypes.put("cer", "application/x-x509-ca-cert");
        mimeTypes.put("crt", "application/x-x509-ca-cert");
        mimeTypes.put("der", "application/x-x509-ca-cert");
        mimeTypes.put("iso", "application/x-iso9660-image");
        // Video
        mimeTypes.put("mpg", "video/mpeg");
        mimeTypes.put("mpe", "video/mpeg");
        mimeTypes.put("mpeg", "video/mpeg");
        mimeTypes.put("qt", "video/quicktime");
        mimeTypes.put("mov", "video/quicktime");
        mimeTypes.put("avi", "video/x-msvideo");
        mimeTypes.put("movie", "video/x-sgi-movie");
        mimeTypes.put("jnlp", "application/x-java-jnlp-file");
        mimeTypes.put("wrl", "x-world/x-vrml");
        mimeTypes.put("vrml", "x-world/x-vrml");
        mimeTypes.put("wml", "text/vnd.wap.wml");
        mimeTypes.put("wmlc", "application/vnd.wap.wmlc");
        mimeTypes.put("wmls", "text/vnd.wap.wmlscript");
    }

    public static String mimeType(File file) {
        String name = file.getName();
        int dotpos = name.lastIndexOf('.');
        if (dotpos == -1)
            return "text/plain";
        else {
            String mimeType = mimeTypes.get(name.substring(dotpos + 1).toLowerCase());
            return (mimeType == null) ? "text/plain" : mimeType;
        }
    }
}