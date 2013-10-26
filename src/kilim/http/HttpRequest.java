/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;

import kilim.Pausable;
import kilim.nio.EndPoint;

/**
 * This object encapsulates a bytebuffer (via HttpMsg). HttpRequestParser creates an instance of this object, but only
 * converts a few of the important fields into Strings; the rest are maintained as ranges (offset + length) in the
 * bytebuffer. Use {@link #getHeader(String)} to get the appropriate field.
 */
public class HttpRequest extends HttpMsg {
    // All the header related members of this class are initialized by the HttpRequestParser class.

    /**
     * The original header. All string variables that pertain to the message's header are either subsequences of this
     * header, or interned (all known keywords).
     */
    public String method;

    /**
     * The UTF8 decoded path from the HTTP header.
     */
    public String uriPath;

    public int    nFields;
    /**
     * Keys present in the HTTP header
     */
    public String keys[];

    // range variables encode the offset and length within the header. The strings corresponding
    // to these variables are created lazily.
    public int    versionRange;
    public int    uriFragmentRange;
    public int    queryStringRange;
    public int[]  valueRanges;

    public int    contentOffset;
    public int    contentLength;

    /**
     * The read cursor, used in the read* methods.
     */
    public int    iread;

    public HttpRequest() {
        keys = new String[5];
        valueRanges = new int[5];
    }

    /** 
     * Get the value for a given key
     * @param key
     * @return null if the key is not present in the header.
     */
    public String getHeader(String key) {
        for (int i = 0; i < nFields; i++) {
            if (key.equalsIgnoreCase(keys[i])) {
                return extractRange(valueRanges[i]);
            }
        }
        return ""; // no point returning null
    }

    /**
     * @return the query part of the URI. 
     */
    public String getQuery() {
        return extractRange(queryStringRange);
    }
    
    public String version() {
        return extractRange(versionRange);
    }
    
    public boolean keepAlive() {
        return isOldHttp() ? "Keep-Alive".equals(getHeader("Connection;")) : !("close".equals(getHeader("Connection")));
    }

    public KeyValues getQueryComponents() {
        String q = getQuery();
        int len = q.length();
        if (q == null || len == 0)
            return new KeyValues(0);

        int numPairs = 0;
        for (int i = 0; i < len; i++) {
            if (q.charAt(i) == '=')
                numPairs++;
        }
        KeyValues components = new KeyValues(numPairs);

        int beg = 0;
        String key = null;
        boolean url_encoded = false;
        for (int i = 0; i <= len; i++) {
            char c = (i == len) ? '&' // pretending there's an artificial marker at the end of the string, to capture
                                      // the last component
                    : q.charAt(i);

            if (c == '+' || c == '%')
                url_encoded = true;
            if (c == '=' || c == '&') {
                String comp = q.substring(beg, i);
                if (url_encoded) {
                    try {
                        comp = URLDecoder.decode(comp, "UTF-8");
                    } catch (UnsupportedEncodingException ignore) {
                    }
                }
                if (key == null) {
                    key = comp;
                } else {
                    components.put(key, comp);
                    key = null;
                }
                beg = i + 1;
                url_encoded = false; // for next time
            }
        }
        return components;
    }

    public String uriFragment() {
        return extractRange(uriFragmentRange);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(500);
        sb.append("method: ").append(method).append('\n').append("version: ").append(version()).append('\n').append(
                "path = ").append(uriPath).append('\n').append("uri_fragment = ").append(uriFragment()).append('\n')
                .append("query = ").append(getQueryComponents()).append('\n');
        for (int i = 0; i < nFields; i++) {
            sb.append(keys[i]).append(": ").append(extractRange(valueRanges[i])).append('\n');
        }

        return sb.toString();
    }

    /**
     * @return true if version is 1.0 or earlier
     */
    public boolean isOldHttp() {
        final byte b1 = (byte) '1';
        int offset = versionRange >> 16;
        return (buffer.get(offset) < b1 || buffer.get(offset + 2) < b1);
    }

    /**
     * Clear the request object so that it can be reused for the next message. 
     */
    public void reuse() {
        method = null;
        uriPath = null;
        versionRange = 0;
        uriFragmentRange = queryStringRange = 0;
        contentOffset = 0;
        contentLength = 0;

        if (buffer != null) {
            buffer.clear();
        }
        for (int i = 0; i < nFields; i++) {
            keys[i] = null;
        }
        nFields = 0;
    }

    
    /*
     * Internal methods 
     */
    public void readFrom(EndPoint endpoint) throws Pausable, IOException {
        iread = 0;
        readHeader(endpoint);
        readBody(endpoint);
    }

    public void readHeader(EndPoint endpoint) throws Pausable, IOException {
        buffer = ByteBuffer.allocate(1024);
        int headerLength = 0;
        int n;
        do {
            n = readLine(endpoint); // includes 2 bytes for CRLF
            headerLength += n;
        } while (n > 2 || headerLength <= 2); // until blank line (CRLF), but just blank line is not enough.
        // dumpBuffer(buffer);
        HttpRequestParser.initHeader(this, headerLength);
        contentOffset = headerLength; // doesn't mean there's necessarily any content.
        String cl = getHeader("Content-Length");
        if (cl.length() > 0) {
            try {
                contentLength = Integer.parseInt(cl);
            } catch (NumberFormatException nfe) {
                throw new IOException("Malformed Content-Length hdr");
            }
        } else if ((getHeader("Transfer-Encoding").indexOf("chunked") >= 0)
                || (getHeader("TE").indexOf("chunked") >= 0)) {
            contentLength = -1;
        } else {
            contentLength = 0;
        }
    }

    public void dumpBuffer(ByteBuffer buffer) {
        byte[] ba = buffer.array();
        int len = buffer.position();
        for (int i = 0; i < len; i++) {
            System.out.print((char) ba[i]);
        }
    }

    public void addField(String key, int valRange) {
        if (keys.length == nFields) {
            keys = (String[]) Utils.growArray(keys, 5);
            valueRanges = Utils.growArray(valueRanges, 5);
        }
        keys[nFields] = key;
        valueRanges[nFields] = valRange;
        nFields++;
    }


    // complement of HttpRequestParser.encodeRange
    public String extractRange(int range) {
        int beg = range >> 16;
        int end = range & 0xFFFF;
        return extractRange(beg, end);
    }

    public String extractRange(int beg, int end) {
        return new String(buffer.array(), beg, (end - beg));
    }


    /*
     * Read entire content into request's buffer
     */
    public void readBody(EndPoint endpoint) throws Pausable, IOException {
        iread = contentOffset;
        if (contentLength > 0) {
            fill(endpoint, contentOffset, contentLength);
            iread = contentOffset + contentLength;
        } else if (contentLength == -1) {
            // CHUNKED
            readAllChunks(endpoint);
        }
        readTrailers(endpoint);
    }

    public void readTrailers(EndPoint endpoint) {
    }

    /*
     * Read all chunks until  a chunksize of 0 is received, then consolidate the chunks into a single contiguous chunk.
     * At the end of this method, the entire content is available in the requests buffer, starting at contentOffset and
     * of length contentLength.
     */
    public void readAllChunks(EndPoint endpoint) throws IOException, Pausable {
        IntList chunkRanges = new IntList(); // alternate numbers in this list refer to the start and end offsets of chunks.
        do {
            int n = readLine(endpoint); // read chunk size text into buffer
            int beg = iread;
            int size = parseChunkSize(buffer, iread - n, iread); // Parse size in hex, ignore extension
            if (size == 0)
                break;
            // If the chunk has not already been read in, do so
            fill(endpoint, iread, size+2 /*chunksize + CRLF*/);
            // record chunk start and end
            chunkRanges.add(beg); 
            chunkRanges.add(beg + size); // without the CRLF
            iread += size + 2; // for the next round.
        } while (true);

        // / consolidate all chunkRanges
        if (chunkRanges.numElements == 0) {
            contentLength = 0;
            return;
        }
        contentOffset = chunkRanges.get(0); // first chunk's beginning
        int endOfLastChunk = chunkRanges.get(1); // first chunk's end

        byte[] bufa = buffer.array();
        for (int i = 2; i < chunkRanges.numElements; i += 2) {
            int beg = chunkRanges.get(i);
            int chunkSize = chunkRanges.get(i + 1) - beg;
            System.arraycopy(bufa, beg, bufa, endOfLastChunk, chunkSize);
            endOfLastChunk += chunkSize;
        }
        // TODO move all trailer stuff up
        contentLength = endOfLastChunk - contentOffset;
        
        // At this point, the contentOffset and contentLen give the entire content 
    }
    

    public static byte CR = (byte) '\r';
    public static byte LF = (byte) '\n';
    static final byte  b0 = (byte) '0', b9 = (byte) '9';
    static final byte  ba = (byte) 'a', bf = (byte) 'f';
    static final byte  bA = (byte) 'A', bF = (byte) 'F';
    static final byte  SEMI = (byte)';';

    public static int parseChunkSize(ByteBuffer buffer, int start, int end) throws IOException {
        byte[] bufa = buffer.array();
        int size = 0;
        for (int i = start; i < end; i++) {
            byte b = bufa[i];
            if (b >= b0 && b <= b9) {
                size = size * 16 + (b - b0);
            } else if (b >= ba && b <= bf) {
                size = size * 16 + ((b - ba) + 10);
            } else if (b >= bA && b <= bF) {
                size = size * 16 + ((b - bA) + 10);
            } else if (b == CR || b == SEMI) { 
                // SEMI-colon starts a chunk extension. We ignore extensions currently.
                break;
            } else {
                throw new IOException("Error parsing chunk size; unexpected char " + b + " at offset " + i);
            }
        }
        return size;
    }

    // topup if request's buffer doesn't have all the bytes yet.
    public void fill(EndPoint endpoint, int offset, int size) throws IOException, Pausable {
        int total = offset + size;
        int currentPos = buffer.position();
        if (total > buffer.position()) {
            buffer = endpoint.fill(buffer, (total - currentPos));
        }
    }

    public int readLine(EndPoint endpoint) throws IOException, Pausable {
        int ireadSave = iread;
        int i = ireadSave;
        while (true) {
            int end = buffer.position();
            byte[] bufa = buffer.array();
            for (; i < end; i++) {
                if (bufa[i] == CR) {
                    ++i;
                    if (i >= end) {
                        buffer = endpoint.fill(buffer, 1);
                        bufa = buffer.array(); // fill could have changed the buffer.
                        end = buffer.position();
                    }
                    if (bufa[i] != LF) {
                        throw new IOException("Expected LF at " + i);
                    }
                    ++i;
                    int lineLength = i - ireadSave;
                    iread = i;
                    return lineLength;
                }
            }
            buffer = endpoint.fill(buffer, 1); // no CRLF found. fill a bit more and start over.
        }
    }
}