package parallel;

import jdk.internal.misc.Unsafe2;

import java.lang.reflect.Field;

public class Native {

    public final long startIndex;

    public static Unsafe2 initUnsafe() {
        Field f = null;
        try {
            f = Unsafe2.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe2) f.get(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Unsafe2 unsafe = initUnsafe();

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
