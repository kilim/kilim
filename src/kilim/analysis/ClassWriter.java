package kilim.analysis;

import kilim.mirrors.Detector;
import kilim.mirrors.RuntimeClassMirrors;

public class ClassWriter extends org.objectweb.asm.ClassWriter {
	private final Detector detector;
	
	public ClassWriter(final int flags, final ClassLoader classLoader) {
		super(flags);
		this.detector = new Detector(new RuntimeClassMirrors(classLoader));
	}

	public ClassWriter(final int flags, final Detector detector) {
		super(flags);
		this.detector = detector;
	}

	protected String getCommonSuperClass(final String type1, final String type2) {
		try {
			return detector.commonSuperType(type1, type2);
		} catch (kilim.mirrors.ClassMirrorNotFoundException e) {
			return "java/lang/Object";
		}
	}
	
}
