/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.nio;

import java.io.IOException;

import kilim.Scheduler;
import kilim.Task;

public class SessionTask extends Task {
  public EndPoint endpoint;
  public Scheduler preferredScheduler;
  
  public EndPoint getEndPoint() {
    return endpoint;
  }
  
  public void setEndPoint(EndPoint ep) throws IOException {
    this.endpoint = ep;
  }
  
  public void close() {
    if (endpoint != null) {
      endpoint.close();
    }
  }
}
