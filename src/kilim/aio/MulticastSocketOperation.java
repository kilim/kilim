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

package kilim.aio;

import java.io.IOException;
import java.io.EOFException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

public final class MulticastSocketOperation extends AsynchronousOperation{

    private final MulticastSocket socket;
    private final InetAddress address;
    private final int port;
    private final Class<? extends Task> receiver;

    public MulticastSocketOperation(String addr, int port, 
                                    Class<? extends Task> receiver){
        this.port = port; // warn port value < 1024 ?

        if(null == addr) 
            throw new NullPointerException("Address is not provided.");

        InetAddress adrs = null;
        try{
            adrs = InetAddress.getByName(addr);
        }catch(UnknownHostException uhe){
            uhe.printStackTrace();
        }
        this.address = adrs;
        if(null == this.address) 
            throw new NullPointerException("Fail initializing InetAddress.");

        if(!this.address.isMulticastAddress())
            throw new IllegalArgumentException("Invalid multicast address.");

        MulticastSocket skt = null;
        try{
            skt = new MulticastSocket(port);  
        }catch(IOException ioe){
            ioe.printStackTrace();
        }
        this.socket = skt;
        if(null == this.socket) 
            throw new NullPointerException("Socket initialization failure.");

        if(null == receiver) 
            throw new NullPointerException("No class for receiving packet");

        this.receiver = receiver;
    }

    public void terminate(){
        try{
            this.socket.leaveGroup(this.address);
            this.socket.close();
        }catch(IOException ioe){
            ioe.printStackTrace();
        }
    }
  
    @Override
    public void execute() throws Pausable, IOException{
        this.socket.joinGroup(this.address);
        while(true){ 
            byte[] msg = new byte[1024];
            DatagramPacket pkg = new DatagramPacket(msg, msg.length);
            this.socket.receive(pkg);
            //if(null == pkg || 0 >= pkg.getLength() ) throw new EOFException();
            // terminate multicast socket
            if(-1 == pkg.getData()[0]) {
                this.terminate();
                throw new EOFException(); 
            }
            try{
                Task tsk = receiver.newInstance();
                ((MulticastSocketReceiver)tsk).endpoint().putnb(pkg);
                tsk.start(); 
            }catch(InstantiationException ie){
                ie.printStackTrace();
            }catch(IllegalAccessException iae){
                iae.printStackTrace();
            }
        }
    }
}
