/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim;

import org.objectweb.asm.Opcodes;

public interface Constants extends Opcodes {
    
    String KILIM_VERSION    = "0.7.3";
    
    // Type descriptors
    String D_BOOLEAN        = "Z";
    String D_BYTE           = "B";
    String D_CHAR           = "C";
    String D_DOUBLE         = "D";
    String D_FLOAT          = "F";
    String D_INT            = "I";
    String D_LONG           = "J";
    String D_SHORT          = "S";
    String D_VOID           = "V";

    String D_ARRAY_BOOLEAN  = "[Z";
    String D_ARRAY_BYTE     = "[B";
    String D_ARRAY_CHAR     = "[C";
    String D_ARRAY_DOUBLE   = "[D";
    String D_ARRAY_FLOAT    = "[F";
    String D_ARRAY_SHORT    = "[S";
    String D_ARRAY_INT      = "[I";
    String D_ARRAY_LONG     = "[J";

    String D_NULL           = "NULL";
    String D_RETURN_ADDRESS = "A";
    String D_OBJECT         = "Ljava/lang/Object;";
    String D_STRING         = "Ljava/lang/String;";
    String D_THROWABLE      = "Ljava/lang/Throwable;";
    String D_UNDEFINED      = "UNDEFINED";

    String D_FIBER          = "Lkilim/Fiber;";
    String D_STATE          = "Lkilim/State;";
    String D_TASK           = "Lkilim/Task;";
    String D_PAUSABLE       = "Lkilim/Pausable;";

    String THROWABLE_CLASS  = "java/lang/Throwable";
    String FIBER_CLASS      = "kilim/Fiber";
    String STATE_CLASS      = "kilim/State";
    String TASK_CLASS       = "kilim/Task";
    String PAUSABLE_CLASS   = "kilim/Pausable";
    String NOT_PAUSABLE_CLASS   = "kilim/NotPausable";
    
    String  WOVEN_FIELD     = "$isWoven";

    // Constant opcodes missing from asm's opcodes (as of asm 3.0)
    int    ILOAD_0          = 26;
    int    LLOAD_0          = 30;
    int    FLOAD_0          = 34;
    int    DLOAD_0          = 38;
    int    ALOAD_0          = 42;
    int    ISTORE_0         = 59;
    int    LSTORE_0         = 63;
    int    FSTORE_0         = 67;
    int    DSTORE_0         = 71;
    int    ASTORE_0         = 75;
    int    LDC2_W           = 20;
}
