package kilim.mirrors;

public abstract class Mirrors {

	abstract public ClassMirror classForName(String className)
		throws ClassMirrorNotFoundException;

	public abstract ClassMirror  mirror(Class<?> clazz);
    public abstract ClassMirror  mirror(String className, byte[] bytecode);
	
	public static Mirrors getRuntimeMirrors() {
		return new RuntimeClassMirrors();
	}

}
