/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.http;

import java.io.IOException;

import kilim.Scheduler;
import kilim.nio.NioSelectorScheduler;

/**
 * A very rudimentary HTTP server bound to a specific given port. 
 */
public class HttpServer {
  public NioSelectorScheduler nio;

  public HttpServer() {}
  
  /**
   * Creates a separate thread and a selector for the select loop and calls {@link #listen(int, Class)}listen
   * 
   * @param port. Port to listen for http connections.
   * @param httpSessionClass An instance of the supplied class is created and bound to the
   * incoming socket connection, and the task is scheduled for execution on the default scheduler. 
   * @throws IOException
   */
  
  public HttpServer(int port, Class<? extends HttpSession> httpSessionClass) throws IOException {
    nio = new NioSelectorScheduler();
    listen(port, httpSessionClass, Scheduler.getDefaultScheduler());
  }
  
  /**
   * Sets up a listener on the supplied port, and when a fresh connection comes in, it creates
   * a new instance of the httpSessionClass task and exceutes it on the supplied scheduler.  
   * It is the httpSession task's responsbility to close the socket. 
   * 
   * @param port. Port to listen for http connections.
   * @param httpSessionClass      class of task to instantiation on incoming connection
   * @param httpSessionScheduler  the scheduler on which to schedule the http session task.
   * @throws IOException
   */
  public void listen(int port, Class<? extends HttpSession> httpSessionClass, Scheduler httpSessionScheduler) throws IOException {
    nio.listen(port, httpSessionClass, httpSessionScheduler);
  }
}
