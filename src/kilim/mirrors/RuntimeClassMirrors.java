package kilim.mirrors;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import org.objectweb.asm.Type;

class RuntimeClassMirrors extends Mirrors {

	static class RuntimeMemberMirror implements MemberMirror {

		private final Member member;

		public RuntimeMemberMirror(Member member) {
			this.member = member;
		}

		public String getName() {
			return member.getName();
		}

	}

	static class RuntimeFieldMirror extends RuntimeMemberMirror implements FieldMirror {

		final private Field field;

		public RuntimeFieldMirror(Field field) {
			super(field);
			this.field = field;
		}

		public static FieldMirror forField(Field member) {
			if (member == null) return null;
			else return new RuntimeFieldMirror(member);
		}

		public ClassMirror getType() {
			return RuntimeClassMirror.forClass(field.getType());
		}
	}
	
	static class RuntimeMethodMirror extends RuntimeMemberMirror implements MethodMirror {

		private final Method method;

		public RuntimeMethodMirror(Method method) {
			super(method);
			this.method = method;
		}

		static MethodMirror forMethod(Method method) {
			if (method==null) return null;
			else return new RuntimeMethodMirror(method);
		}
		
		public static MethodMirror[] forMethods(Method[] declaredMethods) {
			MethodMirror[] result = new MethodMirror[declaredMethods.length];
			for (int i = 0; i < declaredMethods.length; i++) {
				result[i] = RuntimeMethodMirror.forMethod(declaredMethods[i]);
			}
			return result;
		}

		public ClassMirror[] getExceptionTypes() {
			return RuntimeClassMirror.forClasses(method.getExceptionTypes());
		}

		public String getMethodDescriptor() {
			return Type.getMethodDescriptor(method);
		}

		public boolean isBridge() {
			return method.isBridge();
		}

	}

	static class RuntimeClassMirror extends ClassMirror {

		private final Class<?> clazz;
		
		public RuntimeClassMirror(Class<?> clazz) {
			if (clazz == null) throw new NullPointerException();
			this.clazz = clazz;
		}
		
		@Override
		public String getName() {
			return clazz.getName();
		}
		
		@Override
		public boolean isInterface() {
			return clazz.isInterface();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof RuntimeClassMirror) {
				RuntimeClassMirror mirr = (RuntimeClassMirror) obj;

				return mirr.clazz == clazz;
			}

			return false;
		}

		@Override
		public MethodMirror[] getDeclaredMethods() {
			return RuntimeMethodMirror.forMethods(clazz.getDeclaredMethods());
		}

		@Override
		public ClassMirror[] getInterfaces() {
			return forClasses(clazz.getInterfaces());
		}

		private static ClassMirror forClass(Class<?> clazz) {
			if (clazz==null) return null;
			return new RuntimeClassMirror(clazz);
		}
		
		private static ClassMirror[] forClasses(Class<?>[] classes) {
			ClassMirror[] result = new ClassMirror[classes.length];
			for (int i = 0; i < classes.length; i++) {
				result[i] = RuntimeClassMirror.forClass(classes[i]);
			}
			return result;
		}

		@Override
		public ClassMirror getSuperclass() {
			return RuntimeClassMirror.forClass(clazz.getSuperclass());
		}

		@Override
		public boolean isAssignableFrom(ClassMirror c) {
			if (c instanceof RuntimeClassMirror) {
				RuntimeClassMirror cc = (RuntimeClassMirror) c;
				return clazz.isAssignableFrom(cc.clazz);
			} else {
				return false;
			}
		}

	}

	@Override
	public ClassMirror classForName(String className)
			throws ClassMirrorNotFoundException {
		try {
			return new RuntimeClassMirror(Class.forName(className));
		} catch (ClassNotFoundException e) {
			throw new ClassMirrorNotFoundException(e);
		}
	}

	@Override
	public ClassMirror mirror(Class<?> clazz) {
		if (clazz == null) return null;
		return new RuntimeClassMirror(clazz);
	}

	@Override
	public MethodMirror mirror(Method mth) {
		if (mth == null) return null;
		return RuntimeMethodMirror.forMethod(mth);
	}

	@Override
	public FieldMirror mirror(Field member) {
		return RuntimeFieldMirror.forField(member);
	}
	
	@Override
	public MemberMirror mirror(Member member) {
		if (member instanceof Method) {
			return mirror((Method)member);
		} else if (member instanceof Field) {
			return mirror((Field)member);
		} else {
			throw new RuntimeException("member is not field or method?");
		}
	}
}
