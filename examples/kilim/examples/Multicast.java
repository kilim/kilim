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

package kilim.examples;

import java.io.IOException;
import java.net.DatagramPacket;

import kilim.aio.Act;
import kilim.aio.AioFactory;
import kilim.aio.CompletionDispatcher;
import kilim.aio.MulticastSocketHandler;
import kilim.aio.MulticastSocketReceiver;

import kilim.ExitMsg;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

public class Multicast{

    public static class Receiver extends MulticastSocketReceiver{
        @Override
        public void execute() throws Pausable{
            DatagramPacket pkg = endpoint().get();
            System.out.println("Packet received:"+ // customized 
                new String(pkg.getData(),
                pkg.getOffset(), pkg.getLength())
            );
        }
    }

    public static void main(String args[]){
        new Multicast().process();
    }

    void process(){
       final Mailbox<ExitMsg> m = new Mailbox<ExitMsg>();
       CompletionDispatcher proactor =
           AioFactory.createDefaultProactor();
       MulticastSocketHandler h = 
               new MulticastSocketHandler(proactor,"230.0.0.1", 5000, 
                                          Multicast.Receiver.class){
           // customized
           public void notify(Act act) throws IOException{
               System.out.println("finishing async op executing !"); 
               m.putnb(new ExitMsg(act.getCompletionHandler().
                   getCompletionDispatcher(), null)
               );
           }
       }; 
       h.receive();

       proactor.start().informOnExit(m);
       m.getb(); 
       System.exit(0);
    }
}
