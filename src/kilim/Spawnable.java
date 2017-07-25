// Copyright 2014 by sriram - offered under the terms of the MIT License

package kilim;


/**
 * Meant to supply a body to {@code Task#spawn(Spawnable)} 
 */
// this is a @FunctionalInterface, but not annotated to allow java7 compilation
public interface Spawnable<TT> {
    TT execute() throws Pausable;

    public interface Call1<AA> {
        void execute(AA arg1) throws Pausable;
    }
    
    public interface Call {
        void execute() throws Pausable;
    }
}
