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
import java.lang.ref.WeakReference;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Stack;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


/**
 * Utility class to present a uniform iterator interface for file containers; presently 
 * includes directories and jar files.
 */

public class FileLister implements Iterable<FileLister.Entry> {
   public static abstract class Entry {
        public abstract String getFileName();
        public abstract long getSize();
        public abstract InputStream getInputStream() throws IOException;
    };
    
    /**
     * weak ref to a container to avoid hanging on to an open jar file. 
     */
    volatile WeakReference<FileContainer> containerRef;
    String name;
    
    public FileLister(String dirOrJarName) throws IOException {
        name= dirOrJarName;
    }
    
    /**
     * @param relativeFileName
     * @return if the relativeFileName exists in the directory or jar represented by FileLister object
     * open it. If not return null.
     * @throws IOException
     */
    public Entry open(String relativeFileName) throws IOException {
        return getContainer().open(relativeFileName);
    }
    
    // Lazily initialize the container.
    private FileContainer getContainer() throws IOException {
        FileContainer container = null;
        if (containerRef != null) {
           container = containerRef.get();
           if (container != null) return container;
        }
        
        if (name.endsWith(".jar")) {
            container = openJar(this.name);
        } else {
            File f = new File(this.name);
            if (f.exists() && f.isDirectory()) {
                container = new DirIterator(f);
            } else {
                throw new IOException("Expected jar file or directory name");
            }
        }
        containerRef = new WeakReference<FileContainer>(container);
        return container;
    }

    private FileContainer openJar(String jarFile) throws IOException {
        return new JarIterator(new JarFile(jarFile));
    }

    public Iterator<FileLister.Entry> iterator() {
        try {
            return getContainer();
        } catch (IOException ignore) {}
        return null;
    }
}

abstract class FileContainer implements Iterator<FileLister.Entry> {
    abstract FileLister.Entry open(String relativeFileName) throws IOException;
}

/**
 * Preorder traversal of a directory. Returns everything including directory
 * names.
 */
class DirIterator extends FileContainer {
    final File root;
    private static class DirEntry extends FileLister.Entry {
        final File file;
        DirEntry(File f) {file = f;}
        
        @Override
        public long getSize() {
            return file.length();
        }
        @Override
        public String getFileName() {
            try {
                return file.getCanonicalPath();
            } catch (IOException ignore) {}
            return null;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new BufferedInputStream(new FileInputStream(file));
        }
    }
    
    Stack<File> stack = new Stack<File>();
    
    
    DirIterator(File f) {
        root = f;
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

    @Override
    FileLister.Entry open(String fileName) throws IOException {
        File ret = new File(root.getAbsolutePath() + File.separatorChar + fileName);
        if (ret.exists() && ret.isFile()) {
            return new DirEntry(ret);
        }
        return null;
    }
}

class JarIterator extends FileContainer {
    Enumeration<JarEntry> jarEnum;
    JarFile   jarFile;
    String    nextName;
    
    private class JEntry extends FileLister.Entry {
        private final JarEntry jarEntry;
        JEntry(JarEntry j) {jarEntry = j;}
        
        @Override
        public String getFileName() {
            return jarEntry.getName();
        }
        
        @Override
        public InputStream getInputStream() throws IOException {
            return jarFile.getInputStream(jarEntry);
        }
        
        @Override
        public long getSize() {
            return jarEntry.getSize();
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

    @Override
    FileLister.Entry open(String relativeFileName) throws IOException {
        JarEntry e = jarFile.getJarEntry(relativeFileName);
        return e == null ? null : new JEntry(e);
    }
}