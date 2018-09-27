/* Copyright (c) 2006, Sriram Srinivasan, nqzero 2016
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
import java.nio.file.Paths;
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
        /**
         * check if a newer version of the file exists in an output directory. false negatives are allowed
         * @param outdir the output directory
         * @return true if the newer version exists
         */
        public boolean check(String outdir) { return false; }
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

    /**
     * check if dst is up to date with src, ie at least as new
     * @param src the source filename
     * @param dst the destination filename
     * @return true if dst exists and is at least as new as src
     */
    public static boolean check(String src,String dst) {
        File infile = new File(src), outfile = new File(dst);
        long dtime = outfile.lastModified();
        return dtime > infile.lastModified();
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
    String rootpath;
    private class DirEntry extends FileLister.Entry {
        final File file;
        DirEntry(File f) {file = f;}
        
        @Override
        public long getSize() {
            return file.length();
        }
        @Override
        public String getFileName() {
            return file.getPath();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new BufferedInputStream(new FileInputStream(file));
        }

        public boolean check(String outdir) {
            String name = getFileName();
            if (rootpath==null || ! name.startsWith(rootpath)) return false;
            String relative = name.substring(rootpath.length());
            File outfile = Paths.get(outdir,relative).toFile();
            return outfile.lastModified() > file.lastModified();
        }
    }
    
    Stack<File> stack = new Stack<File>();
    
    
    DirIterator(File f) {
        root = f;
        rootpath = root.getPath();
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