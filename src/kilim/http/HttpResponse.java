/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.http;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import kilim.Constants;
import kilim.Pausable;
import kilim.nio.EndPoint;
import kilim.nio.ExposedBaos;

/**
 * The response object encapsulates the header and often, but not always, the content. The caller must set all the
 * fields, except for the protocol, server and date. The body of the response (the content) is written to a stream
 * obtained from {@link #getOutputStream()}.
 */
public class HttpResponse extends HttpMsg {
    // Status codes
    public static final byte[]                      ST_CONTINUE                      = "100 Continue\r\n".getBytes();
    public static final byte[]                      ST_SWITCHING_PROTOCOLS           = "101 Switching Protocols\r\n"
                                                                                             .getBytes();

    // Successful status codes

    public static final byte[]                      ST_OK                            = "200 OK\r\n".getBytes();
    public static final byte[]                      ST_CREATED                       = "201 Created\r\n".getBytes();
    public static final byte[]                      ST_ACCEPTED                      = "202 Accepted\r\n".getBytes();
    public static final byte[]                      ST_NON_AUTHORITATIVE             = "203 Non-Authoritative Information\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_NO_CONTENT                    = "204 No Content\r\n".getBytes();
    public static final byte[]                      ST_RESET_CONTENT                 = "205 Reset Content\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_PARTIAL_CONTENT               = "206 Partial Content\r\n"
                                                                                             .getBytes();

    // Redirection status codes

    public static final byte[]                      ST_MULTIPLE_CHOICES              = "300 Multiple Choices\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_MOVED_PERMANENTLY             = "301 Moved Permanently\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_FOUND                         = "302 Found\r\n".getBytes();
    public static final byte[]                      ST_SEE_OTHER                     = "303 See Other\r\n".getBytes();
    public static final byte[]                      ST_NOT_MODIFIED                  = "304 Not Modified\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_USE_PROXY                     = "305 Use Proxy\r\n".getBytes();
    public static final byte[]                      ST_TEMPORARY_REDIRECT            = "307 Temporary Redirect\r\n"
                                                                                             .getBytes();

    // Client error codes

    public static final byte[]                      ST_BAD_REQUEST                   = "400 Bad Request\r\n".getBytes();
    public static final byte[]                      ST_UNAUTHORIZED                  = "401 Unauthorized\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_PAYMENT_REQUIRED              = "402 Payment Required\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_FORBIDDEN                     = "403 Forbidden\r\n".getBytes();
    public static final byte[]                      ST_NOT_FOUND                     = "404 Not Found\r\n".getBytes();
    public static final byte[]                      ST_METHOD_NOT_ALLOWED            = "405 Method Not Allowed\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_NOT_ACCEPTABLE                = "406 Not Acceptable\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_PROXY_AUTHENTICATION_REQUIRED = "407 Proxy Authentication Required\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_REQUEST_TIMEOUT               = "408 Request Time-out\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_CONFLICT                      = "409 Conflict\r\n".getBytes();
    public static final byte[]                      ST_GONE                          = "410 Gone\r\n".getBytes();
    public static final byte[]                      ST_LENGTH_REQUIRED               = "411 Length Required\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_PRECONDITION_FAILED           = "412 Precondition Failed\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_REQUEST_ENTITY_TOO_LARGE      = "413 Request Entity Too Large\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_REQUEST_URI_TOO_LONG          = "414 Request-URI Too Large\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_UNSUPPORTED_MEDIA_TYPE        = "415 Unsupported Media Type\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_REQUEST_RANGE_NOT_SATISFIABLE = "416 Requested range not satisfiable\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_EXPECTATION_FAILED            = "417 Expectation Failed\r\n"
                                                                                             .getBytes();

    // Server error codes

    public static final byte[]                      ST_INTERNAL_SERVER_ERROR         = "500 Internal Server Error\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_NOT_IMPLEMENTED               = "501 Not Implemented\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_BAD_GATEWAY                   = "502 Bad Gateway\r\n".getBytes();
    public static final byte[]                      ST_SERVICE_UNAVAILABLE           = "503 Service Unavailable\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_GATEWAY_TIMEOUT               = "504 Gateway Time-out\r\n"
                                                                                             .getBytes();
    public static final byte[]                      ST_HTTP_VERSION_NOT_SUPPORTED    = "505 HTTP Version not supported\r\n"
                                                                                             .getBytes();

    // Http response components
    public static final byte[]                      PROTOCOL                         = "HTTP/1.1 ".getBytes();
    public static final byte[]                      F_SERVER                         = ("Server: kilim "
                                                                                             + Constants.KILIM_VERSION + "\r\n")
                                                                                             .getBytes();
    public static final byte[]                      F_DATE                           = "Date: ".getBytes();
    public static final byte[]                      CRLF                             = "\r\n".getBytes();
    public static final byte[]                      FIELD_SEP                        = ": ".getBytes();

    public static ConcurrentHashMap<String, byte[]> byteCache                        = new ConcurrentHashMap<String, byte[]>();

    /**
     * The status line for the response. Can use any of the predefined strings in HttpResponse.ST_*.
     */
    public byte[]                                   status;
    public ArrayList<String>                        keys                             = new ArrayList<String>();
    public ArrayList<String>                        values                           = new ArrayList<String>();
    public ExposedBaos                              bodyStream;

    public static final SimpleDateFormat            gmtdf;

    static {
        gmtdf = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss");
        gmtdf.setTimeZone(TimeZone.getTimeZone("GMT:00"));
    }

    public HttpResponse() {
        this(ST_OK);
    }

    public HttpResponse(byte[] statusb) {
        status = statusb;
    }

    public void reuse() {
        status = ST_OK;
        keys.clear();
        values.clear();
        if (bodyStream != null) {
            bodyStream.reset();
        }
        if (buffer != null) {
            buffer.clear();
        }
    }

    public void setStatus(String statusMsg) {
        if (!statusMsg.endsWith("\r\n")) {
            statusMsg = statusMsg + "\r\n";
        }
        this.status = statusMsg.getBytes();
    }

    public HttpResponse(String statusMsg) {
        this();
        setStatus(statusMsg);
    }

    public HttpResponse addField(String key, String value) {
        keys.add(key);
        values.add(value);
        return this;
    }

    public void writeHeader(OutputStream os) throws IOException {
        DataOutputStream dos = new DataOutputStream(os);
        dos.write(PROTOCOL);
        dos.write(status);

        dos.write(F_DATE);
        byte[] date = gmtdf.format(new Date()).getBytes();
        dos.write(date);
        dos.write(CRLF);

        dos.write(F_SERVER);

        if (bodyStream != null) {
            setContentLength(bodyStream.size());
        }

        // Fields.
        int nfields = keys.size();
        for (int i = 0; i < nfields; i++) {
            String key = keys.get(i);
            byte[] keyb = byteCache.get(key);
            if (keyb == null) {
                keyb = key.getBytes();
                byteCache.put(key, keyb);
            }
            dos.write(keyb);
            dos.write(FIELD_SEP);
            dos.write(values.get(i).getBytes());
            dos.write(CRLF);
        }
        dos.write(CRLF);
    }

    public OutputStream getOutputStream() {
        if (bodyStream == null)
            bodyStream = new ExposedBaos(2048);
        return bodyStream;
    }

    public void writeTo(EndPoint endpoint) throws IOException, Pausable {
        ExposedBaos headerStream = new ExposedBaos();
        writeHeader(headerStream);
        ByteBuffer bb = headerStream.toByteBuffer();
        endpoint.write(bb);
        if (bodyStream != null && bodyStream.size() > 0) {
            bb = bodyStream.toByteBuffer();
            endpoint.write(bb);
        }
    }

    public void setContentLength(long length) {
        addField("Content-Length", Long.toString(length));
    }

    public void setContentType(String contentType) {
        addField("Content-Type", contentType);
    }
}