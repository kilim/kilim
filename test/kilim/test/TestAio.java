/* 
 * Copyright (c) 2010 ChiaHung Lin
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package kilim.test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import junit.framework.TestCase;
import junit.framework.Assert;

import kilim.Task;
import kilim.Mailbox;
import kilim.ExitMsg;
import kilim.Pausable;

import kilim.aio.Act;
import kilim.aio.AioFactory;
import kilim.aio.CompletionHandler;
import kilim.aio.MulticastSocketHandler;
import kilim.aio.MulticastSocketReceiver;
import kilim.aio.CompletionDispatcher;

public class TestAio extends TestCase{
    static String MULTICAST_ADDRESS = "230.0.0.1";
    static int PORT = 5000;
    static int NUM = 100;

    @Override
    public void setUp() throws Exception{
        new Multicast(MULTICAST_ADDRESS, PORT).start();
    }

    @Override
    public void tearDown() throws Exception{
    }

    public void testClient() throws Exception{
        for(int i=0;i<NUM; i++){
            new Client("Oh Hai!".getBytes()).start();
        }
    }

    public void testTermination() throws Exception{
        byte[] ex = new byte[1024];
        ex[0] = -1;
        new Client(ex).start();
    }

    private static class Client extends Task{
        byte[] msg;
        public Client(byte[] msg){
            this.msg = msg;
        }

        public void execute() throws Pausable, Exception{
            MulticastSocket socket = new MulticastSocket(PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            socket.joinGroup(group);
            DatagramPacket packet = 
                new DatagramPacket(msg, msg.length, group, PORT);
            socket.send(packet);
            socket.leaveGroup(group);
            socket.close();
        }
    }
}

class Multicast extends Task{
    String addr;
    int p;

    Multicast(String addr, int p){
        this.addr = addr;
        this.p = p;
    }

    static class Receiver extends MulticastSocketReceiver{
        @Override
        public void execute() throws Pausable{
            DatagramPacket pkg = endpoint().get();
            Assert.assertNotNull("DatagramPacket should not be null.", pkg);
            if(-1 != pkg.getData()[0])
                Assert.assertEquals("Packet legnth.", "Oh Hai!".length(), 
                pkg.getLength());
        }
    }

    public void execute() throws Pausable{
       final Mailbox<ExitMsg> m = new Mailbox<ExitMsg>();
       final CompletionDispatcher proactor =
           AioFactory.createDefaultProactor();
       MulticastSocketHandler h = 
               new MulticastSocketHandler(proactor,this.addr, this.p, 
                                          Multicast.Receiver.class){
           public void notify(Act act) throws IOException{
               Assert.assertNotNull(act);
               CompletionHandler ch = act.getCompletionHandler();
               CompletionDispatcher ptr = ch.getCompletionDispatcher();
               Assert.assertEquals("Proactor should be the same.", 
                   proactor, ptr);
               m.putnb(new ExitMsg(proactor, null));
           }
       }; 
       h.receive();

       proactor.start().informOnExit(m);
       m.getb(); 
       System.exit(0);
    }

}
