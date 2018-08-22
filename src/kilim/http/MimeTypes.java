/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.http;

import java.io.File;
import java.util.HashMap;

/**
 * mime types to simplify building http servers.
 * this class is for convenience and doesn't attempt to be canonical
 */
public class MimeTypes {
    private static HashMap<String,String> mimeTypes = new HashMap<String,String>();

    static {
        mimeTypes.put("html","text/html");
        mimeTypes.put("htm","text/html");
        mimeTypes.put("txt","text/plain");
        mimeTypes.put("xml","text/xml");
        mimeTypes.put("css","text/css");
        mimeTypes.put("sgml","text/x-sgml");
        mimeTypes.put("sgm","text/x-sgml");
        // Images
        mimeTypes.put("gif","image/gif");
        mimeTypes.put("jpg","image/jpeg");
        mimeTypes.put("jpeg","image/jpeg");
        mimeTypes.put("png","image/png");
        mimeTypes.put("bmp","image/bmp");
        mimeTypes.put("tif","image/tiff");
        mimeTypes.put("tiff","image/tiff");
        mimeTypes.put("rgb","image/x-rgb");
        mimeTypes.put("xpm","image/x-xpixmap");
        mimeTypes.put("xbm","image/x-xbitmap");
        mimeTypes.put("svg","image/svg+xml ");
        mimeTypes.put("svgz","image/svg+xml ");
        // Audio
        mimeTypes.put("au","audio/basic");
        mimeTypes.put("snd","audio/basic");
        mimeTypes.put("mid","audio/mid");
        mimeTypes.put("midi","audio/mid");
        mimeTypes.put("rmi","audio/mid");
        mimeTypes.put("kar","audio/mid");
        mimeTypes.put("mpga","audio/mpeg");
        mimeTypes.put("mp2","audio/mpeg");
        mimeTypes.put("mp3","audio/mpeg");
        mimeTypes.put("wav","audio/wav");
        mimeTypes.put("aiff","audio/aiff");
        mimeTypes.put("aifc","audio/aiff");
        mimeTypes.put("aif","audio/x-aiff");
        mimeTypes.put("ra","audio/x-realaudio");
        mimeTypes.put("rpm","audio/x-pn-realaudio-plugin");
        mimeTypes.put("ram","audio/x-pn-realaudio");
        mimeTypes.put("sd2","audio/x-sd2");
        // Applications
        mimeTypes.put("bin","application/octet-stream");
        mimeTypes.put("dms","application/octet-stream");
        mimeTypes.put("lha","application/octet-stream");
        mimeTypes.put("lzh","application/octet-stream");
        mimeTypes.put("exe","application/octet-stream");
        mimeTypes.put("dll","application/octet-stream");
        mimeTypes.put("class","application/octet-stream");
        mimeTypes.put("hqx","application/mac-binhex40");
        mimeTypes.put("ps","application/postscript");
        mimeTypes.put("eps","application/postscript");
        mimeTypes.put("pdf","application/pdf");
        mimeTypes.put("rtf","application/rtf");
        mimeTypes.put("doc","application/msword");
        mimeTypes.put("ppt","application/powerpoint");
        mimeTypes.put("fif","application/fractals");
        mimeTypes.put("p7c","application/pkcs7-mime");
        // Application/x
        mimeTypes.put("js","application/x-javascript");
        mimeTypes.put("z","application/x-compress");
        mimeTypes.put("gz","application/x-gzip");
        mimeTypes.put("tar","application/x-tar");
        mimeTypes.put("tgz","application/x-compressed");
        mimeTypes.put("zip","application/x-zip-compressed");
        mimeTypes.put("dvi","application/x-dvi");
        mimeTypes.put("tex","application/x-tex");
        mimeTypes.put("latex","application/x-latex");
        mimeTypes.put("tcl","application/x-tcl");
        mimeTypes.put("cer","application/x-x509-ca-cert");
        mimeTypes.put("crt","application/x-x509-ca-cert");
        mimeTypes.put("der","application/x-x509-ca-cert");
        mimeTypes.put("iso","application/x-iso9660-image");
        // Video
        mimeTypes.put("mpg","video/mpeg");
        mimeTypes.put("mpe","video/mpeg");
        mimeTypes.put("mpeg","video/mpeg");
        mimeTypes.put("qt","video/quicktime");
        mimeTypes.put("mov","video/quicktime");
        mimeTypes.put("avi","video/x-msvideo");
        mimeTypes.put("movie","video/x-sgi-movie");
        mimeTypes.put("jnlp","application/x-java-jnlp-file");
        mimeTypes.put("wrl","x-world/x-vrml");
        mimeTypes.put("vrml","x-world/x-vrml");
        mimeTypes.put("wml","text/vnd.wap.wml");
        mimeTypes.put("wmlc","application/vnd.wap.wmlc");
        mimeTypes.put("wmls","text/vnd.wap.wmlscript");
    }

    /**
     * get the mime type of a file based on it's filename extension
     * @param file a file
     * @return the mime type, or text/plain if not found
     */
    public static String mimeType(File file) {
        return mimeType(file.getName());
    }
    /**
     * get the mime type of a filename based on it's extension
     * @param name the name of a file
     * @return the mime type, or text/plain if not found
     */
    public static String mimeType(String name) {
        int dotpos = name.lastIndexOf('.');
        if (dotpos==-1)
            return "text/plain";
        else {
            String mimeType = mimeTypes.get(name.substring(dotpos+1).toLowerCase());
            return (mimeType==null) ? "text/plain" : mimeType;
        }
    }
    
    public static HashMap<String,String> cloneMap() {
        return (HashMap<String,String>) mimeTypes.clone();
    }
}
