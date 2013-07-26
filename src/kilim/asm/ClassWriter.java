package kilim.asm;

public class ClassWriter extends org.objectweb.asm.ClassWriter {
	private final ClassLoader classLoader;
	
	public ClassWriter(final int flags, final ClassLoader classLoader) {
		super(flags);
		this.classLoader = classLoader;
	}

	protected String getCommonSuperClass(final String type1, final String type2) {
		Class<?> c, d;
        try {
            c = classForName(type1.replace('/', '.'));
            d = classForName(type2.replace('/', '.'));
        } catch (Exception e) {
            throw new RuntimeException(e.toString());
        }
        if (c.isAssignableFrom(d)) {
            return type1;
        }
        if (d.isAssignableFrom(c)) {
            return type2;
        }
        if (c.isInterface() || d.isInterface()) {
            return "java/lang/Object";
        } else {
            do {
                c = c.getSuperclass();
            } while (!c.isAssignableFrom(d));
            return c.getName().replace('.', '/');
        }
	}
	
	protected Class<?> classForName(String name) throws ClassNotFoundException {
        try {
            if (classLoader != null) {
                return Class.forName(name, false, classLoader);
            }
        } catch (Throwable e) {
            // ignore
        }
        return Class.forName(name, false, getClass().getClassLoader());
    }
}
