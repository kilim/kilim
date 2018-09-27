/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.test;

import junit.framework.TestCase;
import kilim.Pausable;
import kilim.Task;

/**
 * 
 * 
 */
public class TestAbstractExtends extends TestCase {
    static String msg = "aaaaaa";

	public static interface Service {
		public void service(String message) throws Pausable;
	}
	
	public static abstract class StandardService implements Service {

		@Override
		public void service(String message) throws Pausable {
			doService(message);
		}
		
		public abstract void doService(String message) throws Pausable;

	}
	
	public static class MyService extends StandardService {

		@Override
		public void doService(String message) throws Pausable {
                    if (message != msg)
                        fail("mismatch between expected and received messages");
		}
	}
	
	public void testRun() {
		Task task = new Task() {
			public void execute() throws kilim.Pausable ,Exception {
				Service service = new MyService();
				service.service(msg);
			};
		};
		
		task.start();
		task.joinb();
	}
}
