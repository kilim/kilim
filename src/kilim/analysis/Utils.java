/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.analysis;

/**
 * Simple string utils for pretty printing support
 *
 */
public class Utils {
    public static String indentStr = "";
    public static String spaces    = "                                        ";

    public static void indentWith(String s) {
        indentStr = indentStr + s;
    }

    public static void indent(int numSpaces) {
        indentWith(spaces.substring(0, numSpaces));
    }

    public static void dedent(int numSpaces) {
        indentStr = indentStr.substring(0, indentStr.length() - numSpaces);
    }

    public static String format(String s) {
        if (indentStr.length() == 0)
            return s;
        int i = s.indexOf('\n'); // i is always the index of newline
        if (i >= 0) {
            StringBuffer sb = new StringBuffer(100);
            sb.append(indentStr); // leading indent
            int prev = 0; // prev value of i in loop
            do {
                // copy from prev to i (including \n)
                sb.append(s, prev, i + 1);
                // add indentation wherever \n occurs
                sb.append(indentStr);
                prev = i + 1;
                if (prev >= s.length())
                    break;
                i = s.indexOf('\n', prev);
            } while (i != -1);
            // copy left over chars from the last segment
            sb.append(s, prev, s.length());
            return sb.toString();
        } else {
            return indentStr + s;
        }
    }

    public static void resetIndentation() {
        indentStr = "";
    }

    public static void p(String s) {
        System.out.print(format(s));
    }

    public static void pn(String s) {
        System.out.println(format(s));
    }

    public static void pn(int i) {
        System.out.println(format("" + i));
    }

    public static void pn() {
        System.out.println();
    }

    public static void pn(Object o) {
        pn((o == null) ? "null" : o.toString());
    }
}
