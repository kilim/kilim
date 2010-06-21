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
     * Read <code>atleastN</code> bytes more into the buffer if there's space. Otherwise, allocate a bigger 
     * buffer that'll accomodate the earlier contents and atleastN more bytes. 
     * 
     * @param buf
     *            ByteBuffer to be filled
     * @param atleastN
     *            At least this many bytes to be read.
     * @throws IOException
     */
    public ByteBuffer fill(ByteBuffer buf, int atleastN) throws IOException, Pausable {
        if (buf.remaining() < atleastN) {
            ByteBuffer newbb = ByteBuffer.allocate(Math.max(buf.capacity() * 3 / 2, buf.position() + atleastN));
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
                throw new EOFException();
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
        } while (atleastN > 0);
        return buf;
    }
 

    /**
     * Reads a length-prefixed message in its entirety.
     * 
     * @param bb The bytebuffer to fill, assuming there is sufficient space (including the bytes for the length). Otherwise, the
     * contents are copied into a sufficiently big buffer, and the new buffer is returned.
     * 
     * @param lengthLength The number of bytes occupied by the length. Must be 1, 2 or 4, assumed to be in big-endian order.
     * @param lengthIncludesItself  true if the packet length includes lengthLength
     * @return the buffer bb passed in if the message fits or a new buffer. Either way, the buffer returned has the  entire
     * message including the initial length prefix.
     * @throws IOException
     * @throws Pausable
     */
    public ByteBuffer fillMessage(ByteBuffer bb, int lengthLength, boolean lengthIncludesItself) throws IOException, Pausable {
        int pos = bb.position();
        int opos = pos; // save orig pos 
        bb = fill(bb, lengthLength);
        byte a, b, c, d;
        a = b = c = d = 0;
        switch (lengthLength) {
            case 4: a = bb.get(pos); pos++;   
                    b = bb.get(pos); pos++;  // fall through
            case 2: c = bb.get(pos); pos++;  // fall through
            case 1: d = bb.get(pos); break; 
            default: throw new IllegalArgumentException("Incorrect lengthLength (may only be 1, 2 or 4): " + lengthLength);
        }
        int contentLen = ((a << 24) + (b << 16) + (c << 8) + (d << 0));
        // TODO: put a limit on len
        if (lengthIncludesItself) {
            contentLen -= lengthLength;
        }
        // If the fill() above hasn't read in all the content, read the remaining
        int remaining = contentLen - (bb.position() - opos - lengthLength);
        if (remaining > 0) {
            bb = fill(bb, remaining);
        } 
        return bb;
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
