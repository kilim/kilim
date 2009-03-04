/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim.analysis;

public class ClassInfo {
    public String className;
    public byte[] bytes;

    public ClassInfo(String aClassName, byte[] aBytes) {
        className = aClassName.replace('.', '/');
        bytes = aBytes;
    }
}
