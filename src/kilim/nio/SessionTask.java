/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.nio;

import kilim.Task;

public class SessionTask extends Task {
  public EndPoint endpoint;
  
  public void close() {
    if (endpoint != null) {
      endpoint.close();
    }
  }
}
