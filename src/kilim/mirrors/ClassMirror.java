package kilim.mirrors;

public abstract class ClassMirror {

	public abstract MethodMirror[] getDeclaredMethods();

	public abstract boolean isAssignableFrom(ClassMirror c);

	public abstract ClassMirror getSuperclass();

	public abstract ClassMirror[] getInterfaces();

	public abstract boolean isInterface();

	public abstract String getName();

}
