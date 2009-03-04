/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.analysis;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Stack;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Utility class to paper over the differences between jar files and
 * directories 

 */
public class FileLister implements Iterable<FileLister.Entry> {
   public static abstract class Entry {
        public abstract String getFileName();
        public abstract InputStream getInputStream() throws IOException;
    };
    
    Iterator<FileLister.Entry> iter;
    
    public FileLister(String dirOrJarName) throws IOException {
        if (dirOrJarName.endsWith(".jar")) {
            iter = openJar(dirOrJarName);
        } else {
            File f = new File(dirOrJarName);
            if (f.exists() && f.isDirectory()) {
                iter = new DirIterator(f);
            } else {
                throw new IOException("Expected jar file or directory name");
            }
        }
    }

    private Iterator<FileLister.Entry> openJar(String jarFile) throws IOException {
        return new JarIterator(new JarFile(jarFile));
    }

    public Iterator<FileLister.Entry> iterator() {
        return iter;
    }
}

/**
 * Preorder traversal of a directory. Returns everything including directory
 * names.
 */
class DirIterator implements Iterator<FileLister.Entry> {
    private static class DirEntry extends FileLister.Entry {
        final File file;
        DirEntry(File f) {file = f;}
        
        public String getFileName() {
            try {
                return file.getCanonicalPath();
            } catch (IOException ignore) {}
            return null;
        }

        public InputStream getInputStream() throws IOException {
            return new BufferedInputStream(new FileInputStream(file));
        }
    }
    
    Stack<File> stack = new Stack<File>();
    
    DirIterator(File f) {
        stack.push(f);
    }

    public boolean hasNext() {
        return !stack.isEmpty();
    }

    public FileLister.Entry next() {
        File ret = stack.pop();
        if (ret.isDirectory()) {
            // prepare for next round
            File[] files = ret.listFiles();
            // first add all directories to stack, then the files, so that
            // all files in a directory are processed continuously
            for (int i = files.length - 1; i >= 0; i--) {
                File ff = files[i];
                if (ff.isDirectory()) {
                    stack.push(ff);
                }
            }
            for (int i = files.length - 1; i >= 0; i--) {
                File ff = files[i];
                if (!ff.isDirectory()) {
                    stack.push(ff);
                }
            }
        }
        return new DirEntry(ret);
    }

    public void remove() {
        throw new RuntimeException("FileLister does not remove files");
    }
}

class JarIterator implements Iterator<FileLister.Entry> {
    Enumeration<JarEntry> jarEnum;
    JarFile   jarFile;
    String    nextName;
    
    private class JEntry extends FileLister.Entry {
        private final JarEntry jarEntry;
        JEntry(JarEntry j) {jarEntry = j;}
        
        public String getFileName() {
            return jarEntry.getName();
        }
        
        public InputStream getInputStream() throws IOException {
            return jarFile.getInputStream(jarEntry);
        }
    }
    
    JarIterator(JarFile f) {
        jarFile = f;
        jarEnum = f.entries();
    }
    
    public boolean hasNext() {
        return jarEnum.hasMoreElements();
    }

    public FileLister.Entry next() {
        return new JEntry(jarEnum.nextElement());
    }

    public void remove() {
        throw new RuntimeException("FileLister does not remove files");
    }
}