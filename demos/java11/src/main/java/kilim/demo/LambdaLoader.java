package kilim.demo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Field;
import kilim.Pausable;
import kilim.Task;
import kilim.tools.Kilim;

/**
 * demo of (de)serializing a complex object with lambdas using a classloader.
 * exposes some of the limitations of openjdk serialization and provides a workaround.
 * the final example crashes to show that limitation
 */
public class LambdaLoader {
    public interface Fork extends Pausable.Fork, Serializable {}
    public static class Demo implements Fork {
        Fork body3 = () -> {
            Task.sleep(50);
            String val = "fork has slept: " + this.getClass();
            System.out.println(val);
        };
        Object body2 = (Fork) () -> {
            String val = "fork has slept: " + this.getClass();
            System.out.println(val);
        };
        public void execute() throws Pausable, Exception { ((Fork) body2).execute();}
    }
    public static Fork cycle(Fork obj) throws Exception {
        System.out.println();
        System.out.println(obj.getClass().getClassLoader());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(obj);
        out.close();
        byte [] bytes = bos.toByteArray();
        Loader loader = new Loader();
        loader.load(LambdaLoader.class);
        loader.load(LambdaLoader.class.getName()+"$Demo");
        ByteArrayInputStream fin = new ByteArrayInputStream(bytes);
        ObjectInputStream in = loader.new ObjectStream(fin);
        Fork next = (Fork) in.readObject();
        in.close();
        System.out.println(next);
        System.out.println(next.getClass());
        System.out.println("___"+next.getClass().getClassLoader());
        return next;
    }
    public static class Loader extends ClassLoader {
        public Loader() { super(LambdaLoader.class.getClassLoader()); }
        void load(Class klass) throws Exception {
            String name = klass.getName();
            load(name);
        }
        void load(String name) throws Exception {
            String cname = cname(name);
            ClassLoader cl = Loader.class.getClassLoader();
            InputStream in = cl.getResourceAsStream(cname);
            byte [] bytes = new byte[1<<18];
            int num = 0, read;
            while ((read=in.read(bytes,num,bytes.length-num)) >= 0)
                num += read;
            if (read >= 0)
                throw new RuntimeException();
            Class<?> c = super.defineClass(name,bytes,0,num);
            super.resolveClass(c);
        }
        public class ObjectStream extends ObjectInputStream {
            public ObjectStream(final InputStream inputStream) throws IOException {
                super(inputStream);
            }
            @Override
            protected Class<?> resolveClass(final ObjectStreamClass klass) throws IOException, ClassNotFoundException {
                try {
                    return Class.forName(klass.getName(),false,Loader.this);
                } catch (ClassNotFoundException ex) {
                    return super.resolveClass(klass);
                }
            }
        }
    }
    static public String cname(String name) { return name.replace( '.', '/' ) + ".class"; }
    public static void main(String[] args) throws Exception {
        if (Kilim.trampoline(false,args)) return;
        Demo demo = new Demo();
        cycle(demo);
        cycle((Fork) demo.body2);
        Fork fork = demo.body3;
        Field [] fields = fork.getClass().getDeclaredFields();
        Object [] vals = new Object[fields.length];
        for (int ii=0; ii < fields.length; ii++) {
            Field field = fields[ii];
            field.setAccessible(true);
            vals[ii] = field.get(fork);
            field.set(fork,null);
        }
        cycle(fork);
        for (int ii=0; ii < fields.length; ii++) {
            Field field = fields[ii];
            field.setAccessible(true);
            field.set(fork,vals[ii]);
        }
        Task.fork(fork).joinb();
        
        // openjdk can't handle typed object graphs during deserialization
        System.out.println("\n\nthis instance fails due to an openjdk limitation (bug ?)");
        cycle(demo.body3);
    }
}
