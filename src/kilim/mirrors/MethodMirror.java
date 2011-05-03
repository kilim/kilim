package kilim.mirrors;



public interface MethodMirror  {
    
    public abstract String getName();
	
	/** @see org.objectweb.asm.Type#getMethodDescriptor(java.lang.reflect.Method) */
	public abstract String getMethodDescriptor();

	public abstract ClassMirror[] getExceptionTypes() throws ClassMirrorNotFoundException;

	public abstract boolean isBridge();

}
