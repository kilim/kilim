/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import junit.framework.TestCase;
import kilim.Pausable;
import kilim.Scheduler;
import kilim.nio.EndPoint;
import kilim.nio.NioSelectorScheduler;
import kilim.nio.SessionTask;

public class TestIO extends TestCase {
    static final int PORT = 9797;
    static final int ITERS = 10;
    static final int NCLIENTS = 100;
    NioSelectorScheduler nio;
    
    @Override
    protected void setUp() throws Exception {
        nio = new NioSelectorScheduler(); // Starts a single thread that manages the select loop
        nio.listen(PORT, EchoServer.class, Scheduler.getDefaultScheduler()); //
    }
    
    @Override
    protected void tearDown() throws Exception {
        nio.shutdown();
        Thread.sleep(500); // Allow the socket to be closed
    }
    
    /**
     * Launch many ping clients, each of which is paired with its own instance of {@link EchoServer}.
     * @throws IOException
     */
    public void testParallelEchoes() throws IOException {
        try {
            for (int i = 0; i < NCLIENTS; i++) {
                client();
            }
        } catch (IOException e) {
            e.printStackTrace();
            fail("IOException " + e.getClass() + ":" + e.getMessage());
        }
    }
    
    public void testDelay() throws IOException {
        SocketChannel sc = SocketChannel.open();
        
        try {
            sc.socket().connect(new InetSocketAddress("localhost", PORT));
            String s = "Iteration #0. DONE"; // Only because EchoServer checks for it. 
            byte[] sbytes = s.getBytes();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(100);
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(sbytes.length);
            dos.write(sbytes);
            
            byte[] sendbuf = baos.toByteArray();
            // Now write the bytes in little dribs and drabs and delaying in between. This tests fill's yield.
            OutputStream os = sc.socket().getOutputStream();
            sendChunkWithDelay(os, sendbuf, 0, 1);  // splitting the length prefix
            sendChunkWithDelay(os, sendbuf, 1, 2);
            sendChunkWithDelay(os, sendbuf, 3, 4); 
            sendChunkWithDelay(os, sendbuf, 7, 3);
            sendChunkWithDelay(os, sendbuf, 10, sendbuf.length - 10); // the rest
            
            // Ideally, would like to simulate flow control on the rcv end as well, but would have to turn off
            // socket buffering on the EchoServer side of things.  
            String rs = rcv(sc); 
            assertEquals(s, rs);
        } catch (IOException e) {
            e.printStackTrace();
            fail("IOException " + e.getClass() + ":" + e.getMessage());
        } 
    }
    

    public void sendChunkWithDelay(OutputStream os, byte[] sendbuf, int offset, int len) throws IOException {
        os.write(sendbuf, offset, len);
        os.flush();
        try {Thread.sleep(100);} catch (InterruptedException ignore) {}
    }
    /**
     * test client side utility function. uses blocking Java I/O to send a length-prefixed string.
     */
    static void send(SocketChannel sc, String s) throws IOException {
        byte[] bytes = s.getBytes();
        int len = bytes.length;
        DataOutputStream dos = new DataOutputStream(sc.socket().getOutputStream());
        dos.writeInt(len);
        dos.write(bytes);
        dos.flush();
    }
        
    /**
     * test client side utility function. uses blocking Java I/O to rcv a length-prefixed string.
     */
    static String rcv(SocketChannel sc) throws IOException {
        // rcv
        DataInputStream dis = new DataInputStream(sc.socket().getInputStream());
        byte[] bytes = new byte[100];
        int len = dis.readInt();
        assertTrue(len < bytes.length);
        int offset = 0;
        while (len > 0) {
            int n = dis.read(bytes, offset, len);
            if (n == -1) {
                throw new IOException("Unexpected termination");
            }
            len -= n;
            offset += n;
        }
        return new String(bytes, 0, offset); // offset contains the length.
    }
    
    
    static void client() throws IOException {
        SocketChannel sc = SocketChannel.open();
        try {
            // Client using regular JDK I/O API. 
            sc.socket().connect(new InetSocketAddress("localhost", PORT));
            for (int i = 0 ; i < ITERS; i++) {
                String s = "Iteration #" + i;
                if (i == ITERS-1) {s += " DONE";}
                send(sc, s);
                String rs = rcv(sc);
                assertEquals(s, rs);
            }
        } finally {
            sc.close();
        }
    }
    

    public static class EchoServer extends SessionTask {
        @Override
        public void execute() throws Pausable, Exception {
            ByteBuffer buf = ByteBuffer.allocate(100);
            EndPoint ep = getEndPoint();
            while (true) {
                buf.clear();
                buf = ep.fillMessage(buf, 4, /*lengthIncludesItself*/ false);
                buf.flip();
                int strlen = buf.getInt();
                String s= new String(buf.array(), 4, strlen);
                //System.out.println ("Rcvd: " + s);
                if (!s.startsWith("Iteration #")) {
                    ep.close();
                    break;
                } 
                buf.position(0); // reset read pos
                ep.write(buf); // echo.
                if (s.endsWith("DONE")) {
                    ep.close();
                    break;
                }
            }
        }
    }
}
