package org.keeber.service.process;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.keeber.service.Constants;
import org.keeber.service.Daemon;
import org.keeber.simpleio.File;
import org.keeber.simpleio.Streams;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class ChannelManager extends Daemon {
  public static final String SERVLET_REF = "Channel Manager";
  private ServletContext context;
  private File cache;

  public static class serializers {

    public static Gson DEFAULT = new GsonBuilder().setPrettyPrinting().create();

    public static Gson BASIC = new GsonBuilder().setPrettyPrinting().setExclusionStrategies(new ExclusionStrategy() {

      @Override
      public boolean shouldSkipField(FieldAttributes f) {
        return !f.getName().matches("name|description|enabled|id");
      }

      @Override
      public boolean shouldSkipClass(Class<?> clazz) {

        return false;
      }
    }).create();
  }

  public ChannelManager(ServletContext context, File cache) {
    super("Channel Manager");
    this.context = context;
    this.context.setAttribute(SERVLET_REF, this);
    this.cache = cache;
  }

  private synchronized void save(ChannelGroup group) throws IOException {
    if (!cache.exists()) {
      cache.mkdirs();
    }
    cache.create(group.getName().concat(".json")).operations.setStringContent(serializers.DEFAULT.toJson(group.getChannels()));
    getLogger().log(Level.INFO, "Cached Group [{0}]", new Object[] {group.getName()});
  }

  private void load() throws IOException {
    File groupCache;
    List<Object> raws;
    Channel<?> channel;
    for (ChannelGroup group : getGroups()) {
      groupCache = cache.create(group.getName().concat(".json"));
      if (groupCache.exists()) {
        getLogger().log(Level.INFO, "Loading cache [{0}]", new Object[] {groupCache.getPath()});
        raws = serializers.DEFAULT.fromJson(groupCache.operations.getStringContent(), new TypeToken<List<Object>>() {}.getType());
        for (Object raw : raws) {
          channel = (Channel<?>) serializers.DEFAULT.fromJson(serializers.DEFAULT.toJson(raw), group.getChannelClass());
          group.getChannels().add(channel);
          getLogger().log(Level.INFO, "Loaded channel [group={0}][class={1}][name={2}][id={3}]", new Object[] {group.getName(), channel.getClass().getName(), channel.getName(), channel.getId()});
        }
      }
    }
  }

  @WebServlet("/channels/*")
  public static class Service extends HttpServlet {

    @Override
    protected void service(final HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      ChannelManager manager = (ChannelManager) getServletContext().getAttribute(SERVLET_REF);
      List<String> path = Constants.getPath(request);
      response.setContentType("application/json");
      if (path.size() == 0) {
        /*
         * LIST ALL GROUPS
         */
        response.getWriter().write(serializers.BASIC.toJson(manager.getGroups()));
      }
      if (path.size() > 0) {
        ChannelGroup group;
        /*
         * GROUP DEFINED
         */
        try {
          group = manager.getGroup(path.remove(0));
        } catch (ItemNotFoundException e) {
          response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
          return;
        }
        if (path.size() == 0) {
          if ("GET".equals(request.getMethod())) {
            /*
             * LIST CHANNELS IN GROUP
             */
            response.getWriter().write(serializers.BASIC.toJson(group.getChannels()));
          }
          if ("POST".equals(request.getMethod())) {
            /*
             * ADD NEW CHANNEL
             */
            Channel<?> stub = (Channel<?>) serializers.BASIC.fromJson(Streams.asString(request.getInputStream()), group.getChannelClass());
            // Safety check
            stub.setEnabled(false);
            Channel<?> c = group.newChannel(stub.getName(), stub.getDescription());
            response.getWriter().write(serializers.DEFAULT.toJson(c));
          }
        }
        if (path.size() >= 1) {
          Channel<?> channel;
          /*
           * SINGLE CHANNEL
           */
          try {
            channel = group.getChannel(path.remove(0));
          } catch (ItemNotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            return;
          }
          if (path.isEmpty()) {
            if ("PUT".equals(request.getMethod())) {
              /*
               * SAVE / UPDATE CHANNEL
               */
              Channel<?> update = (Channel<?>) serializers.DEFAULT.fromJson(Streams.asString(request.getInputStream()), group.getChannelClass());
              group.updateChannel(channel, update);
              response.getWriter().write(serializers.DEFAULT.toJson(channel));
            }
            if ("GET".equals(request.getMethod())) {
              /*
               * WRITE SINGLE CHANNEL
               */
              response.getWriter().write(serializers.DEFAULT.toJson(channel));
            }
            if ("DELETE".equals(request.getMethod())) {
              group.deleteChannel(channel);
              /*
               * Return new list of channels
               */
              response.getWriter().write(serializers.BASIC.toJson(group.getChannels()));
            }
          }
          if (path.size() >= 1) {
            if (path.remove(0).equals("api")) {
              if (channel.isEnabled()) {
                channel.onService(request, response, path);
              } else {
                response.sendError(HttpServletResponse.SC_EXPECTATION_FAILED, "Channel [id=" + channel.getId() + "] is NOT enabled.");
                return;
              }
            }
          }
        }
      }

    }

  }



  @Override
  protected void onInterval(int interval) {

  }

  @Override
  protected boolean onStart() {
    try {
      load();
    } catch (IOException e) {
      getLogger().log(Level.SEVERE, "Error loading channel groups", e);
    }
    for (ChannelGroup group : getGroups()) {
      getLogger().log(Level.INFO, "Initializing channel [group={0}][class={1}][id={3}]", new Object[] {group.getName(), group.getChannelClass().getName(), group.getId()});
      group.initializeAll();
    }

    return true;
  }

  @Override
  protected boolean onStop() {
    for (ChannelGroup group : getGroups()) {
      getLogger().log(Level.INFO, "[{0}] Shutting down channel [group={1}][class={2}][id={3}]", new Object[] {name, group.getName(), group.getChannelClass().getName(), group.getId()});
      group.shutdownAll();
    }
    return true;
  }

  @Override
  protected void onStateChange(ServiceState state) {

  }

  public void addChannelGroup(String groupName, String description, Class<?> channelClass) {
    channelMap.put(groupName, new ChannelGroup(channelClass, groupName, description));
  }



  public Collection<ChannelGroup> getGroups() {
    return channelMap.values();
  }

  public ChannelGroup getGroup(String identifier) throws ItemNotFoundException {
    /*
     * Identifier can be name or ID.
     */
    for (ChannelGroup group : getGroups()) {
      if (group.getName().equals(identifier) || group.getId().equals(identifier)) {
        return group;
      }
    }

    throw new ItemNotFoundException("Groups", identifier);
  }

  private Map<String, ChannelGroup> channelMap = new LinkedHashMap<String, ChannelGroup>();

  protected class ChannelGroup {
    private Class<?> channelClass;
    private List<Channel<?>> channels = new ArrayList<Channel<?>>();
    private String name;
    private String description;
    private String id;

    public ChannelGroup(Class<?> channelClass, String name, String description) {
      this.channelClass = channelClass;
      this.name = name;
      this.description = description;
      this.id = Constants.uniqueFromString("CG", name);
    }

    protected Class<?> getChannelClass() {
      return channelClass;
    }

    protected List<Channel<?>> getChannels() {
      return channels;
    }

    protected String getName() {
      return name;
    }

    protected String getId() {
      return id;
    }

    protected String getDescription() {
      return description;
    }

    public Channel<?> getChannel(String identifier) throws ItemNotFoundException {
      /*
       * Identifier can be name or ID.
       */
      for (Channel<?> channel : getChannels()) {
        if (channel.getName().equals(identifier) || channel.getId().equals(identifier)) {
          return channel;
        }
      }
      throw new ItemNotFoundException("Channels", identifier);
    }

    public Channel<?> newChannel(String name, String description) {
      Channel<?> c = null;
      try {
        c = (Channel<?>) getChannelClass().newInstance();
        c.setName(name == null ? "Channel " + (getChannels().size() + 1) : name);
        c.setDescription(description == null ? "" : description);
        c.onNew();
        getChannels().add(initilizeChannel(c));
        getLogger().log(Level.INFO, "Added channel [name={0}][description={1}]", new Object[] {c.getName(), c.getDescription()});
      } catch (InstantiationException e) {
        getLogger().log(Level.SEVERE, null, e);
      } catch (IllegalAccessException e) {
        getLogger().log(Level.SEVERE, null, e);
      }
      try {
        save(this);
      } catch (IOException e) {
        getLogger().log(Level.SEVERE, null, e);
      }
      return c;
    }

    @SuppressWarnings("unchecked")
    public void updateChannel(@SuppressWarnings("rawtypes") Channel channel, @SuppressWarnings("rawtypes") Channel update) throws IOException {
      channel.setName(update.getName());
      channel.setDescription(update.getDescription());
      channel.setEnabled(update.isEnabled());
      if (update.getConfiguration() != null) {
        Object conf = channel.getConfiguration().getClass().cast(update.getConfiguration());
        channel.setConfiguration(conf);
      }
      getLogger().log(Level.INFO, "Updated channel [group={0}][class={1}][name={2}][id={3}]", new Object[] {getName(), channel.getClass().getName(), channel.getName(), channel.getId()});
      /*
       * Update
       */
      initilizeChannel(channel);
      save(this);

    }

    private void deleteChannel(Channel<?> channel) throws IOException {
      getLogger().log(Level.INFO, "Delete channel [group={0}][class={1}][name={2}][id={3}]", new Object[] {getName(), channel.getClass().getName(), channel.getName(), channel.getId()});
      channel.onDestroy();
      channels.remove(channel);
      save(this);
    }

    private void shutdownAll() {
      for (Channel<?> channel : channels) {
        getLogger().log(Level.INFO, "[{0}] Shutdown channel [group={1}][class={2}][name={3}][id={4}]", new Object[] {name, getName(), channel.getClass().getName(), channel.getName(), channel.getId()});
        channel.onDestroy();
      }
    }

    private Channel<?> initilizeChannel(Channel<?> channel) {
      channel.setContext(context);
      channel.setLogger(getLogger());
      channel.onUpdate();
      return channel;
    }

    private void initializeAll() {
      for (Channel<?> channel : channels) {
        initilizeChannel(channel);
      }
    }

  }



  public class ItemNotFoundException extends Exception {

    public ItemNotFoundException(String category, String item) {
      super("The item [" + item + "] was not found in [" + category + "]");
    }

  }


}
