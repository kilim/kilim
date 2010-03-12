/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import java.lang.reflect.Field;

/**
 * This class serves as a context to manage and store the continuation stack.
 * The actual capture of the closure is done in the Weaver-transformed code.
 */

public final class Fiber {

//    public boolean debug = false;
    /**
     * The current frame's state (local vars  and elements of the operand stack 
     * that will be needed when the Fiber is resumed. It is always kept equal 
     * to stateStack[iStack] if iStack is in the (0..stateStack.length-1) range, 
     * and null otherwise. This is used by the generated code to avoid 
     * having to manipulate stateStack in the generated code, and to isolate
     * all stack manipulations to up() and down().
     */
    public State               curState;

    /**
     * The "program counter", kept equal to stateStack[iStack].pc and is used to
     * jump to the appropriate place in the code while rewinding the code, and
     * also to inform the weaved code inside an exception handler which pausable
     * method (if at all) was being invoked when that exception was thrown. The
     * value 0 refers to normal operation; control transfers to the beginning of
     * the original (pre-weaved) code. A value of n indicates a direct jump into 
     * the nth pausable method (after restoring the appropriate state).
     * Accessed by generated code (hence public).
     */
    public int                 pc;

    /*
     * One State object for each activation frame in the call hierarchy.
     */
    private State[]            stateStack              = new State[10];

    /*
     * Index into stateStack and equal to depth of call hierarchy - 1
     */
    private int                iStack                  = -1;

    boolean                    isPausing;
    
    boolean                    isDone;

    /*
     * The task to which this Fiber belongs
     */
    public Task                      task;

    /*
     * Special marker state used by pause
     */
    private static final State PAUSE_STATE             = new State();

    /*
     * Status indicators returned by down()
     *
     * normal return, nothing to restore
     */
    public static final int   NOT_PAUSING__NO_STATE  = 0;
    
    /*
     * Normal return, have saved state to restore before resuming
     */
    public static final int   NOT_PAUSING__HAS_STATE = 1;
    
    /*
     * Pausing, and need to save state before returning
     */
    public static final int   PAUSING__NO_STATE      = 2;
    
    /*
     * Pausing, and have saved state from an earlier invocation,
     * so nothing left to do.
     */
    public static final int   PAUSING__HAS_STATE     = 3;

    static {
        PAUSE_STATE.pc = 1;
    }
    
    public Fiber(Task t) {
        task = t;
    }

    public Task task() {
        return task;
    }

    public boolean isDone() {
        return isDone;
    }
    
    public static void pause() throws Pausable {
        throw new IllegalStateException("pause() called without weaving");
    }

    
    /*
     * The user calls pause(), but the weaver changes the
     * call to pause(Fiber), which alternates between
     * pausing and not pausing in successive invocations.
     * @param f
     */
    public static void pause(Fiber f) {
        f.togglePause();
    }
    
    /*
     * Indication from the caller that the invoked function has returned.
     * 
     * If it is a normal return, it needs to read the state into curState
     * and nullify it in stateStack. This accomplishes the equivalent of:
     *      move up
     *      get state()
     *      restore from state
     *      delete state()

     * If it is pausing, it merely needs to return a combined status
     * 
     * @return a combined status of PAUSING/NOT_PAUSING and HAS_STATE/NO_STATE.
     */
    
    public int up() {
        int d = iStack;
        iStack = --d;
        if (isPausing) {
//            if (debug) System.out.println("\nup(pausing)" + this);;
//            if (debug) ds();
            return (stateStack[d] == null) ? PAUSING__NO_STATE
                    : PAUSING__HAS_STATE;
            // not setting curState because the generated code is only
            // interested in knowing whether we have state or not.
        } else {
            // move up to caller's level
            State[] stack = stateStack;
            State cs = curState = stack[d];
            if (cs == null) {
                pc = 0;
//                if (debug) System.out.println("\nup(not pausing)" + this);;
//                if (debug) ds();
                return NOT_PAUSING__NO_STATE;
            } else {
                stack[d] = null; // clean up
                pc = cs.pc;
//                if (debug) System.out.println("\nup(not pausing)" + this);;
//                if (debug) ds();
                return NOT_PAUSING__HAS_STATE;
              }
        }
    }
    
    
    public final Fiber begin() {
        return down();
    }
    
    /**
     * end() is the last up(). returns true if the fiber is not pausing.
     */
    public final boolean end() {
        assert iStack == 0 : "Reset: Expected iStack == 0, not " + iStack + "\n" + this;
        boolean isDone = !isPausing;
        
        if (isDone) {
            // clean up callee's state
            stateStack[0] = null;
        }
        // reset pausing for next round.
        isPausing = false;
        iStack = -1;
//        if (debug) System.out.println("lastUp() " + this);
//        if (debug) ds();
        return isDone;
    }

    /*
     * Called by the generated code to indicate that it is moving on to the next
     * pausable method in the hierarchy Adjust iStack, set curState and pc for
     * convenience
     * 
     * @return this Fiber.
     */
    public Fiber down() {
        int d = ++iStack;
        if (d >= stateStack.length) {
//            System.out.println("size == " + d);
            ensureSize(d * 2);
            pc = 0;
            curState = null;
        } else {
            State s = stateStack[d];
            curState = s;
            pc = (s == null) ? 0 : s.pc;
        }
//        if (debug) System.out.println("down:\n" + this);
//        if (debug) ds();
        return this;
    }
    
    static void ds() {
        for (StackTraceElement ste: new Exception().getStackTrace()) {
            String cl = ste.getClassName();
            String meth = ste.getMethodName();
            if (cl.startsWith("kilim.Worker") || meth.equals("go") || meth.equals("ds")) continue;
            String line = ste.getLineNumber() < 0 ? ""  : ":" + ste.getLineNumber();
            System.out.println('\t' + cl + '.' + ste.getMethodName() + 
                    '(' + ste.getFileName() + line + ')');
        }
    }

    /**
     * In the normal (non-exception) scheme of things, the iStack is incremented
     * by down() on the way down and decremented by a corresponding up() when returning 
     * or pausing. If, however, an exception is thrown, we lose track of where we
     * are in the hierarchy. We recalibrate iStack by creating a dummy exception
     * and comparing it to the stack depth of an exception taken earlier.
     * This is done in scheduler.getStackDepth();
     * A sample stack trace of the dummy exception looks as follows
     * <pre>
     *   at kilim.Fiber.upEx(Fiber.java:250)
     *   at kilim.test.ex.ExCatch.normalCatch(ExCatch.java)
     *   at kilim.test.ex.ExCatch.test(ExCatch.java)
     *   at kilim.test.ex.ExCatch.execute(ExCatch.java)
     *   at kilim.Task.runExecute(Task.java)
     *   at kilim.WorkerThread.run(WorkerThread.java:11)
     * </pre>
     * We have to figure out the stack depth (iStack) of the method
     * that caught the exception and called upEx ("normalCatch" here).
     * The call stack below runExecute may be owned by the scheduler, which
     * may permit more than one task to build up on the stack. For this reason,
     * we let the scheduler tell us the depth of upEx below the task's execute().
     * @return Fiber.pc (note: in contrast up() returns status)
     */
    public int upEx() {
        // compute new iStack. 
        int is = task.getStackDepth() - 2; // remove upEx and convert to 0-based index. 
        State cs = stateStack[is];

        for (int i = iStack; i >= is; i--) {
            stateStack[i] = null; // release state
        }

        iStack = is;
        curState = cs;
        return (cs == null) ? 0 : cs.pc;
    }
    
    /**
     * Called by the weaved code while rewinding the stack. If we are about to
     * call a virtual pausable method, we need an object reference on which to
     * call that method. The next state has that information in state.self
     */
    public Object getCallee() {
        assert stateStack[iStack] != PAUSE_STATE : "No callee: this state is the pause state";
        assert stateStack[iStack] != null : "Callee is null";
        return stateStack[iStack + 1].self;
    }

    private State[] ensureSize(int newsize) {
//        System.out.println("ENSURE SIZE = " + newsize);
        State[] newStack = new State[newsize];
        System.arraycopy(stateStack, 0, newStack, 0, stateStack.length);
        stateStack = newStack;
        return newStack;
    }

    /**
     * Called by the generated code before pausing and unwinding its stack
     * frame.
     * 
     * @param state
     */
    public void setState(State state) {
        stateStack[iStack] = state;
        isPausing = true;
//        System.out.println("setState[" + + iStack + "] = " + this);
    }
    
    public State getState() {
      return stateStack[iStack];
    }

    void togglePause() {
        // The client code would have called fiber.down()
        // before calling Task.pause. curStatus would be
        // upto date.
        
        if (curState == null) {
            setState(PAUSE_STATE);
        } else {
            assert curState == PAUSE_STATE : "togglePause: Expected PAUSE_STATE, instead got: iStack == " + iStack + ", state = " + curState;
            stateStack[iStack] = null;
            isPausing = false;
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(40);
        sb.append("iStack = ").append(iStack).append(", pc = ").append(pc);
        if (isPausing) {
            sb.append(" pausing");
        }
        sb.append('\n');
        for (int i = 0; i < stateStack.length; i++) {
            State st = stateStack[i];
            if (st != null) {
                sb.append(st.getClass().getName()).append('[').append(i).append("]: ");
                stateToString(sb, stateStack[i]);
            }
        }
        return sb.toString();
    }
    
    public void wrongPC() {
        throw new IllegalStateException("Wrong pc: " + pc);
    }

    static private void stateToString(StringBuilder sb, State s) {
        if (s == PAUSE_STATE) {
            sb.append("PAUSE\n");
            return;
        }
        Field[] fs = s.getClass().getFields();
        for (int i = 0; i < fs.length; i++) {
            Field f = fs[i];
            sb.append(f.getName()).append(" = ");
            Object v;
            try {
                v = f.get(s);
            } catch (IllegalAccessException iae) {
                v = "?";
            }
            sb.append(' ').append(v).append(' ');
        }
        sb.append('\n');
    }

    void clearPausing() {
        isPausing = false;
    }
}