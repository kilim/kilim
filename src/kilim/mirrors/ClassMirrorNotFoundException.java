// Copyright 2010 by sriram - offered under the terms of the MIT License

package kilim.mirrors;

public class ClassMirrorNotFoundException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5147833200948234264L;

	public ClassMirrorNotFoundException (String msg) {
	    super(msg);
	}
	public ClassMirrorNotFoundException(Throwable cause) {
		super(cause);
	}
	public ClassMirrorNotFoundException(String className,
			ClassNotFoundException e) {
		super(className, e);
	}

}
