/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim.analysis;
import java.util.ArrayList;

/**
 * Just a convenient alias for ArrayList<BasicBlock> 
 */
public class BBList extends ArrayList<BasicBlock>
{
    private static final long serialVersionUID = -1768924113292685739L;
    public BBList() {}
    public BBList(int size) {
        super(size);
    }
}
