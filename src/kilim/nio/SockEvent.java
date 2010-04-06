/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.nio;

import java.nio.channels.spi.AbstractSelectableChannel;

import kilim.Mailbox;

public class SockEvent {
  public SockEvent(Mailbox<SockEvent> aReplyTo, AbstractSelectableChannel ach, int ops) {
    ch = ach;
    interestOps = ops;
    replyTo = aReplyTo;
  }
  
  public int interestOps; // SelectionKey.OP_* ..
  public AbstractSelectableChannel ch;
  public Mailbox<SockEvent> replyTo; 
}
