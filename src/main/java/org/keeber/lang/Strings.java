package org.keeber.lang;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

public class Strings {

  public static String join(String delimiter, Collection<?> c) {
    StringBuilder builder = new StringBuilder();
    Iterator<?> i = c.iterator();
    while (i.hasNext()) {
      builder.append(asString(i.next())).append(i.hasNext() ? delimiter : "");
    }
    return builder.toString();
  }

  public static String join(String delimeter, String... array) {
    return join(delimeter, Arrays.asList(array));
  }

  public static String asString(Object obj) {
    return asString(obj, "");
  }

  public static String asString(Object obj, String nullValue) {
    return obj == null ? nullValue : obj.toString();
  }

  public static boolean functionallyEmpty(String str) {
    return str == null ? true : str.isEmpty();
  }

  public static boolean hasValue(String str) {
    return !functionallyEmpty(str);
  }

  public static StringBuilder build(String initial) {
    return new StringBuilder(initial);
  }

  public static StringBuilder build() {
    return build("");
  }

  public static String formatDate(Date date) {
    return new SimpleDateFormat("yyyy:MM:dd HH:mm:ss.SSS").format(date);
  }

  public static String formatDate(long time) {
    return formatDate(new Date(time));
  }

  public static String formatBytes(long bytes, boolean si) {
    int unit = si ? 1000 : 1024;
    if (bytes < unit)
      return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(unit));
    String pre = ("KMGTPE").charAt(exp - 1)+"";
    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
  }

}
