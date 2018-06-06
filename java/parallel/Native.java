package parallel;

import jdk.internal.misc.Unsafe;

public class Native {

    public final long startIndex;

    private static Class<?> unsafeClass;
    public static Unsafe unsafe = null;

    static {
        try {
            unsafeClass = Class.forName("jdk.internal.misc.Unsafe", false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public Native(long size) {
        startIndex = unsafe.allocateMemory(size);
        unsafe.setMemory(startIndex, size, (byte) 0);
    }

    public void setValue(byte[] a, long index, byte x) {
        unsafe.putByte(a, index(index), x);
    }

    public byte getValue(long index) {
        return unsafe.getByte(index(index));
    }

    private long index(long offset) {
        return startIndex + offset;
    }

    public void destroy() {
        unsafe.freeMemory(startIndex);
    }

}
