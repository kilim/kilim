/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

/**
 * State is the super class for customized State objects generated
 * by ClassWeaver. For example, a customized state object  may
 * look like this:
 * <pre>
 * public final class kilim.S_O2I3 extends kilim.State{
 *   public java.lang.Object f0, f1;
 *   public int f2, f3, f4;
 *   public kilim.S_O2I3();
 * }
 * </pre>
 * This customized class contains slots for two objects and three
 * integers (its name is indicative of this aspect) and is used
 * as a canonical class to store any activation frame that needs
 * to store two objects and three ints.
 *  
 */

public class State {
    public int pc;
    public Object self;
}
