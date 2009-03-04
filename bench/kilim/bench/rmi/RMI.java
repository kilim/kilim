/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.bench.rmi;
import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.Hashtable;
public class RMI {
    public static void main(String[] args) throws Exception {
        int ntimes = args.length == 0 ? 1000 : Integer.parseInt(args[0]);
        Server obj = new Server();
        Ping stub = (Ping) UnicastRemoteObject.exportObject(obj, 0);
        Hashtable<String, String> h = new Hashtable<String, String>();
        h.put("foo", "bar");
        h.put("hello", "world");
        long begin = System.currentTimeMillis();
        for (int i = 0; i < ntimes; i++) {
//            System.out.println("Sending hash " + System.identityHashCode(h));
            stub.ping(i);
        }
        System.out.println("Elapsed (" + ntimes + " iters) : " + 
                (System.currentTimeMillis() - begin) + " millis");
    }
}

interface Ping extends Remote {
//    void ping(Hashtable<String, String> h) throws RemoteException;
  void ping(int i) throws RemoteException;
}

class Server implements Ping {
    public void ping(int i) throws RemoteException {
//        System.out.println(i);
    }
    
}
    
