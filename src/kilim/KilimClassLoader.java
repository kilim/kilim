// Copyright 2011 by sriram - offered under the terms of the MIT License

package kilim;

/**
 * Extends Classloader just to have access to the (protected) findLoadedClass method
 */
public class KilimClassLoader extends ClassLoader {
    public KilimClassLoader() {
        super(KilimClassLoader.class.getClassLoader());
    }

    
    public boolean isLoaded(String className) {
        return super.findLoadedClass(className) != null;
    }
}
