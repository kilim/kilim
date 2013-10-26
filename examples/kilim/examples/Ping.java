/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.examples;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import kilim.Pausable;
import kilim.Scheduler;
import kilim.nio.EndPoint;
import kilim.nio.NioSelectorScheduler;
import kilim.nio.SessionTask;

/**
 * Example showing kilim's support for NIO.
 * Usage: java kilim.examples.Ping -server in one window
 *    and java kilim.examples.Ping -client in one or more windows.
 * The client sends a number of 100 byte packets which are then echoed by the server. 
 */

public class Ping {
    static int PACKET_LEN = 100;
    static boolean server = false;
    static int port = 7262;
    
    public static void main(String args[]) throws Exception {
        if (args.length == 0) {
            usage();
        } else if (args[0].equalsIgnoreCase("-server")) {
            server = true;
        } else if (args[0].equalsIgnoreCase("-client")) {
            server = false;
        } else {
            usage();
        }
        if (args.length > 1)
            parsePort(args[1]);
        System.out.println("kilim.examples.Ping " + (server ? "-server " : "-client ") + port);
        if (server) {
            Server.run();
        } else {
            Client.run();
        }
    }
    /**
     * Server is a SessionTask, which means an instance of it is created by the 
     * NioSelectorScheduler on an incoming connection. 
     * The task contains an endpoint object, the bridge between the NIO system 
     * and Kilim's scheduling. 
     */
    public static class Server extends SessionTask {
        public static void run() throws IOException {
            Scheduler sessionScheduler = Scheduler.getDefaultScheduler(); // The scheduler/thread pool on which all tasks will be run
            NioSelectorScheduler nio = new NioSelectorScheduler(); // Starts a single thread that manages the select loop
            nio.listen(port, Server.class, sessionScheduler); // 
        }
        
        @Override
        public void execute() throws Pausable, Exception {
            System.out.println("[" + this.id + "] Connection rcvd"); 
            try {
                while (true) {
                    EndPoint ep = getEndPoint();
                    ByteBuffer buf = ByteBuffer.allocate(PACKET_LEN);
                    buf = ep.fill(buf, PACKET_LEN); // Pauses until at least PACKET_LEN bytes have been rcvd in buf.
                    System.out.println("[" + this.id + "] Received pkt"); 
                    buf.flip();
                    ep.write(buf);
                    System.out.println("[" + this.id + "] Echoed pkt"); 
                }
            } catch (EOFException eofe) {
                System.out.println("[" + this.id + "] Connection terminated"); 
            } catch (IOException ioe) {
                System.out.println("[" + this.id + "] IO Exception: " + ioe.getMessage()); 
            }
        }
    }
    
    /**
     * The Client is a conventional Java socket application. 
     */
    public static class Client {
        public static void run() throws IOException {
            SocketChannel ch = SocketChannel.open(new InetSocketAddress("localhost", port));

            // Init ping packet 
            ByteBuffer bb = ByteBuffer.allocate(PACKET_LEN);
            for (int i = 0; i < PACKET_LEN; i++) {
                bb.put((byte)i);
            }
            bb.flip();
            
            // Ping 5 times        
            for (int i = 0 ; i < 5; i++) {
                System.out.print("Ping");
                writePkt(ch, bb);
                System.out.println(" .. ");
                // Read echo
                readPkt(ch, bb);
                bb.flip();
                System.out.println("        reply rcvd");
            }
        }

        private static void readPkt(SocketChannel ch, ByteBuffer bb) throws IOException, EOFException {
            int remaining = PACKET_LEN;
            bb.clear();
            while (remaining > 0) {
                int n = ch.read(bb);
                if (n == -1) {
                    ch.close();
                    throw new EOFException();
                }
                remaining -= n;
            }
        }

        private static void writePkt(SocketChannel ch, ByteBuffer bb) throws IOException {
            // Write packet
            int remaining = PACKET_LEN;
            while (remaining > 0) {
                int n = ch.write(bb);
                remaining -= n;
            }
        }
    }
    static private void usage() {
        System.err.println("Run java kilim.examples.Ping -server [port] in one window");
        System.err.println("and      kilim.examples.Ping -client [port] in one or more");
        System.exit(1);
    }
    
    static void parsePort(String portstr) {
        try {
            port = Integer.parseInt(portstr);
        } catch (Exception e) {
            usage();
        }
    }
}
