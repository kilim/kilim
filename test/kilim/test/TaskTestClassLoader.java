/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;

public class TaskTestClassLoader extends ClassLoader {
    static String wclassDir;

    static {
        URL baseURL = Thread.currentThread().getContextClassLoader().getResource("kilim/test/TaskTestClassLoader.class");
        String path = baseURL.getPath();
        wclassDir = path.substring(0, path.indexOf("/classes/")) + "/wclasses/";
    }
    
    public TaskTestClassLoader(ClassLoader aParent) {
        super(aParent);
    }

    @Override
    public Class<?> loadClass(String className, boolean resolve)
                                                           throws ClassNotFoundException {
        Class<?> ret = findLoadedClass(className);
        if (ret == null && className.startsWith("kilim")) {
            File f = new File(wclassDir + className.replace('.', '/') + ".class");
            if (f.exists()) {
                try {
                    byte[] bytes = getBytes(f);
//                    if (resolve) {
                        ret = defineClass(className, bytes, 0, bytes.length);
//                    } 
                } catch (IOException ioe) {
                    System.err.println("Error loading class " + className + " from file " + f.getPath());
                    ioe.printStackTrace();
                    // Not supposed to happen
                    System.exit(1);
                }
            }
        }
        if (ret == null) {
            return resolve ? findSystemClass(className)
                    : getParent().loadClass(className);
        } else {
            return ret;
        }
    }

    private byte[] getBytes(File f) throws IOException {
        int size = (int)f.length();
        byte[] bytes = new byte[size];
        int remaining = size;
        int i = 0;
        FileInputStream fis = new FileInputStream(f);
        while (remaining > 0) {
            int n = fis.read(bytes, i, remaining);
            if (n == -1) break;
            remaining -= n;
            i += n;
        }
        return bytes;
    }
    
    public static void main(String[] args) throws Exception {
        Class<?> c = new TaskTestClassLoader(Thread.currentThread().getContextClassLoader()).loadClass(args[0], true);
        c.newInstance();
    }
}
