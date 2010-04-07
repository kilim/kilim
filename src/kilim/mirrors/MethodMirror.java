package kilim.mirrors;



public interface MethodMirror extends MemberMirror {
	
	/** @see org.objectweb.asm.Type#getMethodDescriptor(java.lang.reflect.Method) */
	public abstract String getMethodDescriptor();

	public abstract ClassMirror[] getExceptionTypes() ;

	public abstract boolean isBridge();

}
