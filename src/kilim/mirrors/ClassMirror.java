package kilim.mirrors;

public interface ClassMirror {

	public abstract MethodMirror[] getDeclaredMethods();

	public abstract boolean isAssignableFrom(ClassMirror c) throws ClassMirrorNotFoundException;

	public abstract String getSuperclass() throws ClassMirrorNotFoundException;

	public abstract String[] getInterfaces() throws ClassMirrorNotFoundException;

	public abstract boolean isInterface();

	public abstract String getName();

}
