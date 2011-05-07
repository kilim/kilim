package kilim.mirrors;

/** 
 * Mirrors provides a uniform facade for class and method related information 
 * (via ClassMirror and MethodMirror). This information is obtained either through 
 * loaded Class objects or parsed bytecode.
 */
public abstract class Mirrors {
	abstract public ClassMirror classForName(String className)
		throws ClassMirrorNotFoundException;

	public abstract ClassMirror  mirror(Class<?> clazz);
    public abstract ClassMirror  mirror(String className, byte[] bytecode);
}
