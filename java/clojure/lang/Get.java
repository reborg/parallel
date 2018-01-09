package clojure.lang;

public class Get {

    public static PersistentHashMap.INode root(PersistentHashMap phm) {
        return phm.root;
    }

    public static Boolean hasNullValue(PersistentHashMap phm) {
        return phm.hasNull;
    }

    public static Object nullValue(PersistentHashMap phm) {
        return phm.nullValue;
    }

    public static PersistentHashMap.INode[] array(PersistentHashMap.ArrayNode arrayNode) {
        return arrayNode.array;
    }

    public static Object[] array(PersistentHashMap.BitmapIndexedNode bitmapIndexedNode) {
        return bitmapIndexedNode.array;
    }

    public static Object[] array(PersistentHashMap.HashCollisionNode hashCollisionNode) {
        return hashCollisionNode.array;
    }

    public static Object kvreduce(Object[] array, IFn f, Object init) {
        return PersistentHashMap.NodeSeq.kvreduce(array, f, init);
    }
}
