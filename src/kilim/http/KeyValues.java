/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.http;


/**
 * A low overhead map to avoid creating too many objects (Entry objects and iterators etc)
 */
public class KeyValues {
  public String[] keys;
  public String[] values;
  public int      count;
  
  public KeyValues() {this(5);}
  public KeyValues(int size) {
    keys = new String[size];
    values = new String[size];
  }
  
  /**
   * @param key
   * @return value for the given key.
   */
  public String get(String key) {
    int i = indexOf(key); 
    return i == -1 ? "" : values[i];
  }
  
  public int indexOf(String key) {
    int len = count;
    for (int i = 0; i < len; i++) {
      if (keys[i].equals(key)) {
        return i;
      }
    }
    return -1;
  }
  
  /**
   * add/replace key value pair. 
   * @param key
   * @param value
   * @return old value
   */
  public void put(String key, String value) {
    int i = indexOf(key); 
    if (i == -1) {
      if (count == keys.length) {
        keys = (String[]) Utils.growArray(keys, count * 2);
        values = (String[]) Utils.growArray(values, count * 2);
      }
      keys[count] = key;
      values[count] = value;
      count++;
    } else { 
      values[i] = value;
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < count; i++) {
      if (i != 0) sb.append(", ");
      sb.append(keys[i]).append(':').append(values[i]);
    }
    sb.append(']');
    return sb.toString();
  }
}
