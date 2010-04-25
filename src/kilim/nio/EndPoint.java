/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim.nio;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;

import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

/**
 * The EndPoint represents an open socket connection. It is a wrapper over a non-blocking socket (channel) and belongs
 * to a {@link SessionTask}. It serves as the bridge between the SessionTask and the {@link NioSelectorScheduler}, using
 * a pair of mailboxes for exchanging socket registration and socket ready events.
 * <p>
 * The other purpose of this class is to provide convenience methods that read from a socket into a bytebuffer, or write
 * from a bytebuffer to the socket. If the socket is not ready for business, the endpoint (and hence the task) simply
 * yields, <i>without</i> registering with the {@link NioSelectorScheduler}. The idea is to give the other runnable
 * tasks a chance to run before retrying the operation (on resumption); this avoids waking up the selector -- an
 * expensive operation -- as much as possible, and introduces a delay between retries. If, after a fixed number of times
 * ({@link #YIELD_COUNT}), the operation still hasn't succeeded, the endpoint registers itself with the
 * {@link NioSelectorScheduler}, and waits for a socket-ready event from the selector.
 * 
 * This scheme is adaptive to load, in that the delay between retries is proportional to the number of runnable tasks.
 * Busy sockets tend to get serviced more often as the socket is always ready.
 */
public class EndPoint extends Mailbox<SockEvent> { // Mailbox for receiving socket ready events.

    // TODO: This too must be made adaptive.
    static final int                 YIELD_COUNT = Integer.parseInt(System.getProperty("kilim.nio.yieldCount", "4"));

    /**
     * The socket channel wrapped by the EndPoint. See #dataChannel()
     */
    public AbstractSelectableChannel sockch;

    /**
     * The NioSelectorScheduler's mailbox to which to send registration events.
     */
    public Mailbox<SockEvent>        sockEvMbx;

    public EndPoint() {
        super(2, 2); // Expecting only one event, but don't want the NioSelectorScheduler to
        // pause for lack of space (due to unforeseen bugs).
    }

    public EndPoint(Mailbox<SockEvent> mbx, AbstractSelectableChannel ch) {
        this.sockch = ch;
        this.sockEvMbx = mbx;
    }

    public SocketChannel dataChannel() {
        return (SocketChannel) sockch;
    }

    /**
     * Write buf.remaining() bytes to dataChannel().
     */
    public void write(ByteBuffer buf) throws IOException, Pausable {
        SocketChannel ch = dataChannel();
        int remaining = buf.remaining();
        if (remaining == 0)
            return;
        int n = ch.write(buf);
        remaining -= n;
        int yieldCount = 0;
        while (remaining > 0) {
            if (n == 0) {
                yieldCount++;
                if (yieldCount < YIELD_COUNT) {
                    Task.yield(); // don't go back to selector yet.
                } else {
                    pauseUntilWritable();
                    yieldCount = 0;
                }
            }
            n = ch.write(buf);
            remaining -= n;
        }
    }
    
    

    /**
     * Read into buffer buf and ensure that buf position > atLeastN before returning.
     * 
     * @param buf
     *            ByteBuffer to be filled
     * @param atleastN
     *            At least this many bytes to be read.
     * @throws IOException
     */
    public ByteBuffer fill(ByteBuffer buf, int atleastN) throws IOException, Pausable {
        if (buf.remaining() < atleastN) {
            ByteBuffer newbb = ByteBuffer.allocate(Math.max(buf.capacity() * 3 / 2, atleastN));
            buf.rewind();
            newbb.put(buf);
            buf = newbb;
        }

        SocketChannel ch = dataChannel();
        if (!ch.isOpen()) {
            throw new EOFException();
        }
        int yieldCount = 0;
        do {
            int n = ch.read(buf);
            // System.out.println(buf);
            if (n == -1) {
                close();
                return buf;
            }
            if (n == 0) {
                yieldCount++;
                if (yieldCount < YIELD_COUNT) {
                    // Avoid registering with the selector because it requires waking up the selector, context switching
                    // between threads and calling the OS just to register. Just yield, let other tasks have a go, then
                    // check later. Do this at most YIELD_COUNT times before going back to the selector.
                    Task.yield();
                } else {
                    pauseUntilReadble();
                    yieldCount = 0;
                }
            }
            atleastN -= n;
        } while (buf.position() < atleastN);
        return buf;
    }

    public void pauseUntilReadble() throws Pausable, IOException {
        SockEvent ev = new SockEvent(this, sockch, SelectionKey.OP_READ);
        sockEvMbx.putnb(ev);
        // TODO. Need to introduce session timeouts
        super.get(); // wait on self
    }

    public void pauseUntilWritable() throws Pausable, IOException {
        SockEvent ev = new SockEvent(this, sockch, SelectionKey.OP_WRITE);
        sockEvMbx.putnb(ev);
        // TODO. Need to introduce session timeouts
        super.get(); // wait on self
    }

    public void pauseUntilAcceptable() throws Pausable, IOException {
        SockEvent ev = new SockEvent(this, sockch, SelectionKey.OP_ACCEPT);
        sockEvMbx.putnb(ev);
        super.get(); // wait on self
    }

    /**
     * Write a file to the endpoint using {@link FileChannel#transferTo}
     * 
     * @param fc       FileChannel to copy to endpoint
     * @param start    Start offset
     * @param length   Number of bytes to be written
     * @throws IOException
     * @throws Pausable
     */
    public void write(FileChannel fc, long start, long length) throws IOException, Pausable {
        SocketChannel ch = dataChannel();
        long remaining = length - start;
        if (remaining == 0)
            return;

        long n = fc.transferTo(start, remaining, ch);
        start += n;
        remaining -= n;
        int yieldCount = 0;
        while (remaining > 0) {
            if (n == 0) {
                yieldCount++;
                if (yieldCount < YIELD_COUNT) {
                    Task.yield(); // don't go back to selector yet.
                } else {
                    pauseUntilWritable();
                    yieldCount = 0;
                }
            }
            n = fc.transferTo(start, remaining, ch);
            start += n;
            remaining -= n;
        }
    }

    /**
     * Close the endpoint
     */
    public void close() {
        try {
            // if (sk != null && sk.isValid()) {
            // sk.attach(null);
            // sk.cancel();
            // sk = null;
            // }
            sockch.close();
        } catch (Exception ignore) {
            ignore.printStackTrace();
        }
    }
}
