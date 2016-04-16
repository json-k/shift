package org.keeber.service.process;

import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.keeber.service.Constants;

public abstract class Channel<C> {
  private C configuration;
  private String name;
  private String description;
  private String id = Constants.uniqueID("CH");
  private boolean enabled = false;
  private transient ServletContext context;
  private transient Logger logger;


  /**
   * <p>
   * Called after the channel has been created (the class object has been created).
   * 
   * <p>
   * This method would be expected to initialize any values that the channel may depend on in
   * operation. <b>Especially the Configuration<C> and Status<S></b>
   */
  public abstract void onNew();

  /**
   * <p>
   * Called before the channel is stopped / deleted.
   * 
   */
  public abstract void onDestroy();

  /**
   * <p>
   * Called when the channel is updated by:
   * 
   * <ul>
   * <li>A configuration change
   * <li>Being enabled / disabled
   * </ul>
   */
  public abstract void onUpdate();

  /**
   * <p>
   * Called when information is posted to the channel.
   * 
   * @param request
   * @param response
   * @param path
   */
  public abstract void onService(final HttpServletRequest request, HttpServletResponse response, List<String> path);

  protected C getConfiguration() {
    return configuration;
  }

  protected void setConfiguration(C configuration) {
    this.configuration = configuration;
  }

  protected ServletContext getContext() {
    return context;
  }

  protected void setContext(ServletContext context) {
    this.context = context;
  }

  protected void setName(String name) {
    this.name = name;
  }

  protected void setDescription(String description) {
    this.description = description;
  }

  public Logger getLogger() {
    return logger;
  }

  protected void setLogger(Logger logger) {
    this.logger = logger;
  }

  protected void setEnabled(boolean enabled) {
    if (this.enabled != enabled) {
      this.enabled = enabled;
    }
  }

  protected boolean isEnabled() {
    return enabled;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getId() {
    return id;
  }

  protected void setId(String id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Channel<?> other = (Channel<?>) obj;
    if (id == null) {
      if (other.id != null)
        return false;
    } else if (!id.equals(other.id))
      return false;
    return true;
  }



}
