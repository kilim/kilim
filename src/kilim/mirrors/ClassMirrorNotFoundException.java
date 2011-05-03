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

}
