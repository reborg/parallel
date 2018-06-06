package jdk.internal.misc;

public final class Unsafe {

    private static native void registerNatives();
    static { registerNatives(); }
    private Unsafe() {}
    public static final Unsafe instance = new Unsafe();

    public void setMemory(Object o, long offset, long bytes, byte value) {
        if (bytes == 0) {
            return;
        }
        setMemory0(o, offset, bytes, value);
    }

    public void setMemory(long address, long bytes, byte value) {
        setMemory(null, address, bytes, value);
    }

    public void freeMemory(long address) {

        if (address == 0) {
            return;
        }

        freeMemory0(address);
    }

    public long allocateMemory(long bytes) {
        if (bytes == 0) {
            return 0;
        }

        long p = allocateMemory0(bytes);
        if (p == 0) {
            throw new OutOfMemoryError();
        }

        return p;
    }

    private native void setMemory0(Object o, long offset, long bytes, byte value);
    public native void putByte(Object o, long offset, byte x);
    private native void freeMemory0(long address);
    public native byte getByte(long address);
    private native long allocateMemory0(long bytes);
    public native byte getByte(Object o, long offset);
    public native void putByte(long address, byte x);
    public native int pageSize();

}