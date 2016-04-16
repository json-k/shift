package org.keeber.lang;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * This class is TOTALLY experimental.
 */
public class Maps {

  /**
   * <p>
   * Creates a map of the provided type from the provided values.
   * 
   * @param values an even numbered object array alternating types K and V
   * @return Map<K,V>
   */
  @SuppressWarnings("unchecked")
  public static <K, V> Map<K, V> from(Object... values) {
    if ((values.length & 1) != 0) {
      throw new RuntimeException("Uneven array length in map creation (I can't build that out of that).");
    }
    Map<K, V> map = new HashMap<K, V>();
    for (int i = 0; i < values.length; i += 2) {
      map.put((K) values[i], (V) values[i + 1]);
    }
    return map;
  }

  /**
   * <p>
   * Creates a map of the provided type from the provided arrays matching the keys and values.
   * 
   * @param keys
   * @param values
   * @return
   */
  public static <K, V> Map<K, V> from(K[] keys, V[] values) {
    if (values.length != keys.length) {
      throw new RuntimeException("Uneven key and value maps (I can't build that out of that).");
    }
    Map<K, V> map = new HashMap<K, V>();
    for (int i = 0; i < values.length; i++) {
      map.put(keys[i], (V) values[i]);
    }
    return map;
  }

}
