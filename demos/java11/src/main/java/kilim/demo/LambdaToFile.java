package kilim.demo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import kilim.Pausable;
import kilim.Task;
import kilim.tools.Kilim;

/**
 * demo of saving and loading a pausable lambda using a file.
 * if the file is present, it's not regenerated.
 * if the file is loaded, it's deleted on exit
 */
public class LambdaToFile {
    public interface SerFork extends Serializable, Pausable.Fork {}

    public static void main(String[] args) throws Exception {
        if (Kilim.trampoline(false,args)) return;
        String name = "file.jobj";
        File file = new File(name);
        if (file.exists())
            file.deleteOnExit();
        else {
            SerFork body = () -> {
                Task.sleep(1000);
                System.out.println("fork has slept: " + args + name);
            };
            System.out.println("saving file: " + name);
            save(body,name);
        }
        SerFork body = (SerFork) load(name);
        Task.fork(body).joinb();
    }
    public static void save(Object obj,String name) throws Exception {
        FileOutputStream fos = new FileOutputStream(name);
        ObjectOutputStream out = new ObjectOutputStream(fos);
        out.writeObject(obj);
        out.close();
        fos.close();
    }
    public static Object load(String name) throws Exception {
        FileInputStream fin = new FileInputStream(name);
        ObjectInputStream in = new ObjectInputStream(fin);
        Object obj = in.readObject();
        in.close();
        fin.close();
        return obj;
    } 
    
}
