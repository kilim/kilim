/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.examples;
import kilim.Generator;
import kilim.Pausable;

/** 
 * This example illustrates two 'generators' that walk a tree, one in pre-order
 * and another in post-order.
 * 
 * A generator is an iterator that generates a value (in this
 * case the nodes of the tree) each time its execute() method
 * 'yields' a value. 
 * 
 * Also, @see kilim.examples.Fib
 */

public class Tree {
    public String _val;
    Tree _left;
    Tree _right;
    
    public static void main(String[] args) {
        Tree t = new Tree("root", 
            new Tree("a", 
                new Tree("a1"),
                new Tree("a2")),
            new Tree("b", 
                new Tree ("b1"),
                new Tree ("b2")));

        System.out.println("Pre-order traversal:");
        for (String s: new Pre(t)) {
            System.out.println(s);
        }

        System.out.println("Post-order traversal");
        for (String s: new Post(t)) {
            System.out.println(s);
        }
    }
    
    Tree(String s) {_val = s;}
    
    Tree(String s, Tree l, Tree r) {this(s); _left = l; _right = r;}
}

class Pre extends Generator<String> {
    Tree _t;
    Pre(Tree t) {_t = t;}
    
    public void execute() throws Pausable{
        walk(_t);
    }
    
    void walk(Tree t) throws Pausable {
        if (t == null) return;
        yield(t._val);
        walk(t._left);
        walk(t._right);
    }
}

class Post extends Generator<String> {
    Tree _t;
    Post(Tree t) {_t = t;}
    
    public void execute() throws Pausable {
        walk(_t);
    }
    
    void walk(Tree t) throws Pausable {
        if (t == null) return;
        walk(t._left);
        walk(t._right);
        yield(t._val);
    }
}