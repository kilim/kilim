// Copyright 2014 by sriram - offered under the terms of the MIT License

package kilim;


/**
 * Meant to supply a body to {@code Task#spawn(Spawnable)} 
 */
// this is a @FunctionalInterface, but not annotated to allow java7 compilation
public interface Spawnable {
    void execute() throws Pausable;
}
