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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + className.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof ClassInfo)) {
			return false;
		}
		ClassInfo other = (ClassInfo) obj;
		if (!className.equals(other.className)) {
			return false;
		}
		return true;
	}
}
