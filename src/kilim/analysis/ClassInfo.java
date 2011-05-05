/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim.analysis;

public class ClassInfo {
    /**
     * fully qualified classname in a format suitable for Class.forName
     */
    public String className;
    
    /**
     * bytecode for the class
     */
    public byte[] bytes;

    public ClassInfo(String aClassName, byte[] aBytes) {
      className = aClassName.replace('/', '.');
//        className = aClassName.replace('.', '/');
        bytes = aBytes;
    }
    
    @Override
    public String toString() {
        return className;
    }
}
