package kilim.mirrors;

import org.objectweb.asm.tree.ClassNode;


public abstract class Mirrors {

	abstract public ClassMirror classForName(String className)
		throws ClassMirrorNotFoundException;

	public abstract ClassMirror  mirror(Class<?> clazz);
    public abstract ClassMirror  mirror(ClassNode classNode);
	
	public static Mirrors getRuntimeMirrors() {
		return new RuntimeClassMirrors();
	}

}
