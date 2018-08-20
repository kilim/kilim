package kilim.tools;


import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.TreeMap;
import kilim.WeavingClassLoader;

public class Agent implements ClassFileTransformer {
    public static TreeMap<String,byte []> map;

    public byte[] transform(
            ClassLoader loader,
            String name,
            Class klass,
            ProtectionDomain protectionDomain,
            byte[] bytes)
            throws IllegalClassFormatException {

        
        String memory = "com.sun.tools.javac.launcher.Main$MemoryClassLoader";
        if (loader != null && memory.equals(loader.getClass().getName())) {
            String cname = WeavingClassLoader.makeResourceName(name);
            map.put(cname,bytes);
        }
        return bytes;
    }

    public static void premain(String agentArgs,Instrumentation inst) {
        if (map==null) map = new TreeMap();
        inst.addTransformer(new Agent());
    }

}
