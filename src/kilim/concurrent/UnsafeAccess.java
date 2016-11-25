// Copyright 2014 by nilangshah - offered under the terms of the MIT License

package kilim.concurrent;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class UnsafeAccess {
    @SuppressWarnings("restriction")
    public static final Unsafe UNSAFE;
    static {
        try {
            @SuppressWarnings("restriction")
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            
            field.setAccessible(true);
            
            UNSAFE = (Unsafe) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
