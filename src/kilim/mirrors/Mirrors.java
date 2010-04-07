package kilim.mirrors;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;


public abstract class Mirrors {

	abstract public ClassMirror classForName(String className)
		throws ClassMirrorNotFoundException;

	public abstract ClassMirror  mirror(Class<?> clazz);
	public abstract MethodMirror mirror(Method mth) ;
	public abstract MemberMirror mirror(Member member) ;
	public abstract FieldMirror  mirror(Field member) ;

	public static Mirrors getRuntimeMirrors() {
		return new RuntimeClassMirrors();
	}

}
