package org.keeber.lang;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

import org.mvel2.templates.TemplateRuntime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class Stringmatic {
  private Gson gson = null;
  private Map<String, Object> resolver = new HashMap<String, Object>();

  public Stringmatic() {
    put("rand", new AtomicInteger(new Random().nextInt(999999999)));
    resolver.put("date", new SMDate());
  }

  public class SMDate extends HashMap<String, String> {

    public SMDate() {
      String sdf = new SimpleDateFormat("yyyyMMddHHmmss.SSS", Locale.ENGLISH).format(new Date());
      this.put("day", sdf.substring(6, 8));
      this.put("decaminute", sdf.substring(10, 11));
      this.put("full", sdf.substring(0, 8));
      this.put("hour", sdf.substring(8, 10));
      this.put("milli", sdf.substring(15, 18));
      this.put("minute", sdf.substring(10, 12));
      this.put("month", sdf.substring(4, 6));
      this.put("second", sdf.substring(12, 14));
      this.put("year", sdf.substring(0, 4));
    }

  }

  public void put(String key, Object value) {
    resolver.put(key, value);
  }

  /**
   * <p>
   * Puts the given matcher as a String Array of it's matched groups into the resolver.
   *
   * @param key to use as key (eg: 'match')
   * @param matcher to convert
   */
  public void put(String key, Matcher matcher) {
    String[] matches = new String[matcher.groupCount() + 1];
    for (int g = 0; g <= matcher.groupCount(); g++) {
      matches[g] = matcher.group(g);
    }
    put(key, matches);
  }

  /**
   * <p>
   * Increments and puts the given Atomic integer into the the resolver as an array of leading zero
   * padded strings with a length from 0 to 10.
   * 
   * @param key
   * @param seed the AtomicInteger that will be incremented.
   */
  public void put(String key, AtomicInteger seed) {
    String formatted = new DecimalFormat("#0000000000").format(seed.incrementAndGet());
    String[] content = new String[formatted.length()];
    for (int i = 0; i < content.length; i++) {
      content[i] = formatted.substring(content.length - i);
    }
    put(key, content);
  }

  /**
   * <p>
   * Delegates to the resolver.
   * 
   * @param key
   * @return
   */
  public Object get(String key) {
    return resolver.get(key);
  }

  public String evaluate(String text) throws EvaluationException {
    String result = "";
    try {
      Object obj = TemplateRuntime.eval(text, resolver);
      result = obj.toString();
    } catch (Exception e) {
      e.printStackTrace();
      throw new EvaluationException(e.getMessage());
    }
    return result;
  }

  public Stringmatic clone() {
    Stringmatic copy = new Stringmatic();
    copy.resolver = getGson().fromJson(getGson().toJson(this.resolver), new TypeToken<HashMap<String, Object>>() {}.getType());
    return copy;
  }

  private Gson getGson() {
    return (gson == null ? gson = new GsonBuilder().setPrettyPrinting().create() : gson);
  }

  public String asJson() {
    return getGson().toJson(resolver);
  }

  /**
   * Exception.
   */
  public static class EvaluationException extends Exception {

    public EvaluationException(String reason) {
      super("Script executiorn error. Cause:" + reason);
    }
  }


}
