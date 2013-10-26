/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.http;

public class IntList {
  public int[] array;
  public int   numElements;
  public IntList(int initialSize) {
    array = new int[initialSize];
  }
  public IntList() {this(10);}
  
  public void add(int element) {
    if (numElements == array.length) {
      array = (int[]) Utils.growArray(array, array.length * 3 / 2 );
    }
    array[numElements++] = element;
  }
  
  public int get(int index) {
    return array[index];
  }
 }
