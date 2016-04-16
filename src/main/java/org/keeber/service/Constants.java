package org.keeber.service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.keeber.simpleio.File;

public class Constants {
  private File home;
  private File conf;
  private Properties properties = new Properties();
  private static final Logger logger = Logger.getLogger(Constants.class.getSimpleName());

  protected Constants() {
    try {
      properties.load(Constants.class.getResourceAsStream(Constants.class.getSimpleName().concat(".properties")));
    } catch (IOException e) {
      logger.log(Level.WARNING, "Failed to load service properties", e);
    }
  }

  public static String getServicename() {
    return In.stance.properties.getProperty("SERVICENAME", "unknown");
  }

  public static String getDescription() {
    return In.stance.properties.getProperty("DESCRIPTION", "unknown");
  }

  public static String getVersion() {
    return In.stance.properties.getProperty("VERSION", "unknown");
  }

  public static String getClassifier() {
    return In.stance.properties.getProperty("CLASSIFIER", "unknown");
  }

  public static String uniqueID(String prefix) {
    return unique(prefix, UUID.randomUUID());
  }

  public static String uniqueFromString(String prefix, String content) {
    return unique(prefix, UUID.nameUUIDFromBytes(content.getBytes()));
  }

  private static String unique(String prefix, UUID id) {
    return prefix.concat(id.toString().toUpperCase().replaceAll("[^0-9A-Z]", ""));
  }

  public static List<String> getPath(HttpServletRequest request) {
    List<String> path = new ArrayList<String>();
    if (request.getPathInfo() != null) {
      String cleanpath = request.getPathInfo().replaceFirst("^/", "");
      cleanpath = cleanpath.replaceFirst("/$", "").replaceAll("\\/+", "/");
      path.addAll(Arrays.asList(cleanpath.split("\\/", -1)));
    }
    return path;
  }

  /**
   * <p>
   * Finds the application configuration directory dynamically. Looks for:
   * 
   * <ul>
   * <li>/usr/local/[servicename].conf/</li>
   * <li>../[servicename].conf</li>
   * <li>[home]/conf/</li>
   * </ul>
   * 
   * <p>
   * And takes the first existing folder.
   * 
   * @param home (this needs to be passed otherwise it may not be initialized).
   * @return Configuration directory
   * @throws IOException
   */
  public static File getConf(File home) throws IOException {
    if (In.stance.conf == null) {
      for (File check : new File[] {File.resolve("/usr/local/".concat(getServicename()).concat(".conf/")), home.parent().create(getServicename().concat(".conf/"))}) {
        if (check.exists() && check.isDirectory()) {
          return In.stance.conf = check;
        }
      }
      return In.stance.conf = home.create("conf/");
    } else {
      return In.stance.conf;
    }
  }

  /**
   * <p>
   * Get the service home - see:{@link #getHome(ServletContext)}.
   * 
   * @return
   * @throws IOException
   */
  public static File getHome() throws IOException {
    return getHome(null);
  }

  /**
   * <p>
   * Get the service home. The home will be:
   * 
   * <dl>
   * <dt>WEB-INF/</dt>
   * <dd>this folder if it contains a 'conf' folder - useful for IDE running (configurations live in
   * this directory during development then move to the service folder for production).</dd>
   * <dt>/usr/local/servicename/</dt>
   * <dd>if it exists</dd>
   * <dt>../</dt>
   * <dd>otherwise the parent folder.</dd>
   * </dl>
   * 
   * @param context (can be null)
   * @return
   * @throws IOException
   */
  public static File getHome(ServletContext context) throws IOException {
    if (In.stance.home == null) {
      File check;
      /*
       * Look in the /usr/local/servicename/ (it might be installed here anyway). If not we assume
       * it's the parent directory to the current.
       */
      for (String path : new String[] {"/usr/local/".concat(getServicename()).concat("/"), "../"}) {
        check = File.resolve(path);
        if (check.exists() && check.isDirectory()) {
          return In.stance.home = check;
        }
      }
      for (String path : new String[] {"../conf/"}) {
        check = File.resolve(path);
        if (check.exists() && check.isDirectory()) {
          return In.stance.home = check.parent();
        }
      }
      /*
       * This checks if we are loading from a web application and sets the home to the WEB-INF if
       * there is a 'conf' directory present.
       */
      if (context != null) {
        check = File.resolve(new java.io.File(context.getRealPath("/WEB-INF/conf/")).toURI().toString());
        if (check.exists()) {
          return In.stance.home = check.parent();
        }
      }

    } else {
      return In.stance.home;
    }
    throw new IOException("Could not find service home.");
  }

  private static class In {
    private static Constants stance = new Constants();
  }

  /**
   * Returns the hostname (assuming it is indeed available (which it sometimes isn't in a DMZ for
   * some reason)).
   */
  public static final String HOSTNAME = getHostname();

  private static String getHostname() {
    String host = UUID.randomUUID().toString().toUpperCase().replaceAll("[^0-9A-Z]", "");
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {

    }
    return host;
  }

}
