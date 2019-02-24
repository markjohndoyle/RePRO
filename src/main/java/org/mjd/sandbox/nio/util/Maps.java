package org.mjd.sandbox.nio.util;

import java.util.Map;
import java.util.function.Supplier;

public class Maps {

    public static <K, V> V getOrCreateFromMap(Map<K, V> map, K key,  Supplier<V> creator) {
        V value = map.get(key);
        if (value == null)
        {
            V newValue = creator.get();
            map.put(key, newValue);
            return newValue;
        }
        return value;
    }




//    public static <K, V> V getFromMap(Map<K, V> map, K key) {
//	return map.get(key);
//    }
}
