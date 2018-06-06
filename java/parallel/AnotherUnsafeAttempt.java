package parallel;

import java.lang.reflect.*;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;

public final class AnotherUnsafeAttempt {

    private static long ADDRESS_FIELD_OFFSET = 0;
    private static long BYTE_ARRAY_BASE_OFFSET = 0;
    private static Constructor<?> DIRECT_BUFFER_CONSTRUCTOR = null;
    private static Method ALLOCATE_ARRAY_METHOD = null;

    private static Throwable UNSAFE_UNAVAILABILITY_CAUSE = null;
    private static Object INTERNAL_UNSAFE = null;

    public static sun.misc.Unsafe UNSAFE = null;

    private static RuntimeException handleInaccessibleObjectException(RuntimeException e) {
        if ("java.lang.reflect.InaccessibleObjectException".equals(e.getClass().getName())) {
            return e;
        }
        throw e;
    }

    public static Throwable trySetAccessible(AccessibleObject object, boolean checkAccessible) {
        try {
            object.setAccessible(true);
            return null;
        } catch (SecurityException e) {
            return e;
        } catch (RuntimeException e) {
            return handleInaccessibleObjectException(e);
        }
    }

    private static final long UNSAFE_COPY_THRESHOLD = 1024L * 1024L;

    private static boolean UNALIGNED = false;

    static {
        final ByteBuffer direct;
        Field addressField = null;
        Method allocateArrayMethod = null;
        Throwable unsafeUnavailabilityCause = null;
        sun.misc.Unsafe unsafe;
        Object internalUnsafe = null;

        direct = ByteBuffer.allocateDirect(1);

        // attempt to access field AnotherUnsafeAttempt#theUnsafe
        final Object maybeUnsafe = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                try {
                    final Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                    // We always want to try using AnotherUnsafeAttempt as the access still works on java9 as well and
                    // we need it for out native-transports and many optimizations.
                    Throwable cause = trySetAccessible(unsafeField, false);
                    if (cause != null) {
                        return cause;
                    }
                    // the unsafe instance
                    return unsafeField.get(null);
                } catch (NoSuchFieldException e) {
                    return e;
                } catch (SecurityException e) {
                    return e;
                } catch (IllegalAccessException e) {
                    return e;
                } catch (NoClassDefFoundError e) {
                    // Also catch NoClassDefFoundError in case someone uses for example OSGI and it made
                    // AnotherUnsafeAttempt unloadable.
                    return e;
                }
            }
        });

        if (maybeUnsafe instanceof Throwable) {
            unsafe = null;
            unsafeUnavailabilityCause = (Throwable) maybeUnsafe;
        } else {
            unsafe = (sun.misc.Unsafe) maybeUnsafe;
        }

        if (unsafe != null) {
            final sun.misc.Unsafe finalUnsafe = unsafe;
            final Object maybeException = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    try {
                        finalUnsafe.getClass().getDeclaredMethod(
                                "copyMemory", Object.class, long.class, Object.class, long.class, long.class);
                        return null;
                    } catch (NoSuchMethodException e) {
                        return e;
                    } catch (SecurityException e) {
                        return e;
                    }
                }
            });

            if (maybeException == null) {
            } else {
                unsafe = null;
                unsafeUnavailabilityCause = (Throwable) maybeException;
            }
        }

        if (unsafe != null) {
            final sun.misc.Unsafe finalUnsafe = unsafe;

            final Object maybeAddressField = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    try {
                        final Field field = Buffer.class.getDeclaredField("address");
                        final long offset = finalUnsafe.objectFieldOffset(field);
                        final long address = finalUnsafe.getLong(direct, offset);

                        // if direct really is a direct buffer, address will be non-zero
                        if (address == 0) {
                            return null;
                        }
                        return field;
                    } catch (NoSuchFieldException e) {
                        return e;
                    } catch (SecurityException e) {
                        return e;
                    }
                }
            });

            if (maybeAddressField instanceof Field) {
                addressField = (Field) maybeAddressField;
            } else {
                unsafeUnavailabilityCause = (Throwable) maybeAddressField;

                unsafe = null;
            }
        }

        if (unsafe != null) {
            long byteArrayIndexScale = unsafe.arrayIndexScale(byte[].class);
            if (byteArrayIndexScale != 1) {
                unsafeUnavailabilityCause = new UnsupportedOperationException("Unexpected unsafe.arrayIndexScale");
                unsafe = null;
            }
        }

        UNSAFE_UNAVAILABILITY_CAUSE = unsafeUnavailabilityCause;
        UNSAFE = unsafe;

        if (unsafe == null) {
            ADDRESS_FIELD_OFFSET = -1;
            BYTE_ARRAY_BASE_OFFSET = -1;
            UNALIGNED = false;
            DIRECT_BUFFER_CONSTRUCTOR = null;
            ALLOCATE_ARRAY_METHOD = null;
        } else {
            Constructor<?> directBufferConstructor;
            long address = -1;
            try {
                final Object maybeDirectBufferConstructor =
                        AccessController.doPrivileged(new PrivilegedAction<Object>() {
                            public Object run() {
                                try {
                                    final Constructor<?> constructor =
                                            direct.getClass().getDeclaredConstructor(long.class, int.class);
                                    Throwable cause = trySetAccessible(constructor, true);
                                    if (cause != null) {
                                        return cause;
                                    }
                                    return constructor;
                                } catch (NoSuchMethodException e) {
                                    return e;
                                } catch (SecurityException e) {
                                    return e;
                                }
                            }
                        });

                if (maybeDirectBufferConstructor instanceof Constructor<?>) {
                    // try to use the constructor now
                    try {
                        ((Constructor<?>) maybeDirectBufferConstructor).newInstance(address, 1);
                        directBufferConstructor = (Constructor<?>) maybeDirectBufferConstructor;
                    } catch (InstantiationException e) {
                        directBufferConstructor = null;
                    } catch (IllegalAccessException e) {
                        directBufferConstructor = null;
                    } catch (InvocationTargetException e) {
                        directBufferConstructor = null;
                    }
                } else {
                    directBufferConstructor = null;
                }
            } finally {
                if (address != -1) {
                    UNSAFE.freeMemory(address);
                }
            }
            DIRECT_BUFFER_CONSTRUCTOR = directBufferConstructor;
            ADDRESS_FIELD_OFFSET = objectFieldOffset(addressField);
            BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
            boolean unaligned;
            Object maybeUnaligned = AccessController.doPrivileged(new PrivilegedAction<Object>() {

                public Object run() {
                    try {
                        Class<?> bitsClass =
                                Class.forName("java.nio.Bits", false, getSystemClassLoader());
                        Method unalignedMethod = bitsClass.getDeclaredMethod("unaligned");
                        Throwable cause = trySetAccessible(unalignedMethod, true);
                        if (cause != null) {
                            return cause;
                        }
                        return unalignedMethod.invoke(null);
                    } catch (NoSuchMethodException e) {
                        return e;
                    } catch (SecurityException e) {
                        return e;
                    } catch (IllegalAccessException e) {
                        return e;
                    } catch (ClassNotFoundException e) {
                        return e;
                    } catch (InvocationTargetException e) {
                        return e;
                    }
                }
            });

            if (maybeUnaligned instanceof Boolean) {
                unaligned = (Boolean) maybeUnaligned;
            } else {
                String arch = System.getProperty("os.arch", "");
                unaligned = arch.matches("^(i[3-6]86|x86(_64)?|x64|amd64)$");
                Throwable t = (Throwable) maybeUnaligned;
            }

            UNALIGNED = unaligned;


            ALLOCATE_ARRAY_METHOD = allocateArrayMethod;
        }

        INTERNAL_UNSAFE = internalUnsafe;

    }

    static boolean isUnaligned() {
        return UNALIGNED;
    }

    static boolean hasUnsafe() {
        return UNSAFE != null;
    }

    static Throwable getUnsafeUnavailabilityCause() {
        return UNSAFE_UNAVAILABILITY_CAUSE;
    }

    static boolean unalignedAccess() {
        return UNALIGNED;
    }

    public static <T> T checkNotNull(T arg, String text) {
        if (arg == null) {
            throw new NullPointerException(text);
        }
        return arg;
    }

    static void throwException(Throwable cause) {
        UNSAFE.throwException(checkNotNull(cause, "cause"));
    }

    static boolean hasDirectBufferNoCleanerConstructor() {
        return DIRECT_BUFFER_CONSTRUCTOR != null;
    }

    static ByteBuffer reallocateDirectNoCleaner(ByteBuffer buffer, int capacity) {
        return newDirectBuffer(UNSAFE.reallocateMemory(directBufferAddress(buffer), capacity), capacity);
    }

    static ByteBuffer allocateDirectNoCleaner(int capacity) {
        return newDirectBuffer(UNSAFE.allocateMemory(capacity), capacity);
    }

    static boolean hasAllocateArrayMethod() {
        return ALLOCATE_ARRAY_METHOD != null;
    }

    static byte[] allocateUninitializedArray(int size) {
        try {
            return (byte[]) ALLOCATE_ARRAY_METHOD.invoke(INTERNAL_UNSAFE, byte.class, size);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        } catch (InvocationTargetException e) {
            throw new Error(e);
        }
    }

    static ByteBuffer newDirectBuffer(long address, int capacity) {

        try {
            return (ByteBuffer) DIRECT_BUFFER_CONSTRUCTOR.newInstance(address, capacity);
        } catch (Throwable cause) {
            // Not expected to ever throw!
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new Error(cause);
        }
    }

    static long directBufferAddress(ByteBuffer buffer) {
        return getLong(buffer, ADDRESS_FIELD_OFFSET);
    }

    static long byteArrayBaseOffset() {
        return BYTE_ARRAY_BASE_OFFSET;
    }

    static Object getObject(Object object, long fieldOffset) {
        return UNSAFE.getObject(object, fieldOffset);
    }

    static int getInt(Object object, long fieldOffset) {
        return UNSAFE.getInt(object, fieldOffset);
    }

    private static long getLong(Object object, long fieldOffset) {
        return UNSAFE.getLong(object, fieldOffset);
    }

    static long objectFieldOffset(Field field) {
        return UNSAFE.objectFieldOffset(field);
    }

    static byte getByte(long address) {
        return UNSAFE.getByte(address);
    }

    static short getShort(long address) {
        return UNSAFE.getShort(address);
    }

    static int getInt(long address) {
        return UNSAFE.getInt(address);
    }

    static long getLong(long address) {
        return UNSAFE.getLong(address);
    }

    static byte getByte(byte[] data, int index) {
        return UNSAFE.getByte(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static short getShort(byte[] data, int index) {
        return UNSAFE.getShort(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static int getInt(byte[] data, int index) {
        return UNSAFE.getInt(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static long getLong(byte[] data, int index) {
        return UNSAFE.getLong(data, BYTE_ARRAY_BASE_OFFSET + index);
    }

    static void putByte(long address, byte value) {
        UNSAFE.putByte(address, value);
    }

    static void putShort(long address, short value) {
        UNSAFE.putShort(address, value);
    }

    static void putInt(long address, int value) {
        UNSAFE.putInt(address, value);
    }

    static void putLong(long address, long value) {
        UNSAFE.putLong(address, value);
    }

    static void putByte(byte[] data, int index, byte value) {
        UNSAFE.putByte(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putShort(byte[] data, int index, short value) {
        UNSAFE.putShort(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putInt(byte[] data, int index, int value) {
        UNSAFE.putInt(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void putLong(byte[] data, int index, long value) {
        UNSAFE.putLong(data, BYTE_ARRAY_BASE_OFFSET + index, value);
    }

    static void copyMemory(long srcAddr, long dstAddr, long length) {
        //UNSAFE.copyMemory(srcAddr, dstAddr, length);
        while (length > 0) {
            long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
            UNSAFE.copyMemory(srcAddr, dstAddr, size);
            length -= size;
            srcAddr += size;
            dstAddr += size;
        }
    }

    static void copyMemory(Object src, long srcOffset, Object dst, long dstOffset, long length) {
        //UNSAFE.copyMemory(src, srcOffset, dst, dstOffset, length);
        while (length > 0) {
            long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
            UNSAFE.copyMemory(src, srcOffset, dst, dstOffset, size);
            length -= size;
            srcOffset += size;
            dstOffset += size;
        }
    }

    static void setMemory(long address, long bytes, byte value) {
        UNSAFE.setMemory(address, bytes, value);
    }

    static void setMemory(Object o, long offset, long bytes, byte value) {
        UNSAFE.setMemory(o, offset, bytes, value);
    }

    static boolean equals(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int length) {
        if (length <= 0) {
            return true;
        }
        final long baseOffset1 = BYTE_ARRAY_BASE_OFFSET + startPos1;
        final long baseOffset2 = BYTE_ARRAY_BASE_OFFSET + startPos2;
        int remainingBytes = length & 7;
        final long end = baseOffset1 + remainingBytes;
        for (long i = baseOffset1 - 8 + length, j = baseOffset2 - 8 + length; i >= end; i -= 8, j -= 8) {
            if (UNSAFE.getLong(bytes1, i) != UNSAFE.getLong(bytes2, j)) {
                return false;
            }
        }

        if (remainingBytes >= 4) {
            remainingBytes -= 4;
            if (UNSAFE.getInt(bytes1, baseOffset1 + remainingBytes) !=
                    UNSAFE.getInt(bytes2, baseOffset2 + remainingBytes)) {
                return false;
            }
        }
        if (remainingBytes >= 2) {
            return UNSAFE.getChar(bytes1, baseOffset1) == UNSAFE.getChar(bytes2, baseOffset2) &&
                    (remainingBytes == 2 || bytes1[startPos1 + 2] == bytes2[startPos2 + 2]);
        }
        return bytes1[startPos1] == bytes2[startPos2];
    }


    static ClassLoader getClassLoader(final Class<?> clazz) {
        if (System.getSecurityManager() == null) {
            return clazz.getClassLoader();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                public ClassLoader run() {
                    return clazz.getClassLoader();
                }
            });
        }
    }

    static ClassLoader getContextClassLoader() {
        if (System.getSecurityManager() == null) {
            return Thread.currentThread().getContextClassLoader();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                public ClassLoader run() {
                    return Thread.currentThread().getContextClassLoader();
                }
            });
        }
    }

    static ClassLoader getSystemClassLoader() {
        if (System.getSecurityManager() == null) {
            return ClassLoader.getSystemClassLoader();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                public ClassLoader run() {
                    return ClassLoader.getSystemClassLoader();
                }
            });
        }
    }

    static int addressSize() {
        return UNSAFE.addressSize();
    }

    static long allocateMemory(long size) {
        return UNSAFE.allocateMemory(size);
    }

    static void freeMemory(long address) {
        UNSAFE.freeMemory(address);
    }

    static long reallocateMemory(long address, long newSize) {
        return UNSAFE.reallocateMemory(address, newSize);
    }

    private AnotherUnsafeAttempt() {
    }
}