/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.http;

import java.lang.reflect.Array;

public class Utils {
  public static Object[] growArray(Object[] input, int extraRoom) {
    int size = input.length + extraRoom;
    Object[] ret = (Object[]) Array.newInstance(input.getClass().getComponentType(), size);
    System.arraycopy(input,0,ret,0,input.length);
    return ret;
  }
  
  public static int[] growArray(int[] input, int extraRoom) {
    int size = input.length + extraRoom;
    int[] ret = (int[]) Array.newInstance(input.getClass().getComponentType(), size);
    System.arraycopy(input,0,ret,0,input.length);
    return ret;
  }
  
}
