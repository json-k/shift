package org.keeber.service.process;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.Timer;

import org.keeber.service.Daemon;
import org.keeber.service.process.Queue.Queueable.Times;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

public class Queue extends Daemon {
  public static class serializers {

    private static JsonSerializer<Times> timeserializer = new JsonSerializer<Times>() {

      @Override
      public JsonElement serialize(Times value, Type type, JsonSerializationContext context) {
        JsonElement o = RAW.toJsonTree(value);
        try {
          o.getAsJsonObject().add("duration", new JsonPrimitive((value.start == 0 ? 0 : (value.end == 0) ? System.currentTimeMillis() - value.start : value.end - value.start)));
          o.getAsJsonObject().add("queued", new JsonPrimitive(value.start == 0 ? System.currentTimeMillis() - value.submitted : value.start - value.submitted));
        } catch (Exception e) {
          //
        }
        return o;
      }

    };


    public static Gson COMPACT = new GsonBuilder().setExclusionStrategies(new ExclusionStrategy() {

      @Override
      public boolean shouldSkipClass(Class<?> arg0) {
        return false;
      }

      @Override
      public boolean shouldSkipField(FieldAttributes fa) {
        return fa.getName().equals("logs");
      }
    }).registerTypeAdapter(Times.class, timeserializer).setPrettyPrinting().create();

    private static Gson RAW = new GsonBuilder().setPrettyPrinting().create();;

    public static Gson DEFAULT = new GsonBuilder().registerTypeAdapter(Times.class, timeserializer).setPrettyPrinting().create();

  }

  @WebServlet("/queue/*")
  public static class Service extends HttpServlet {

    @Override
    protected void service(final HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      Queue queue = (Queue) getServletContext().getAttribute(SERVLET_REF);// Queue.get(getServletContext());
      if (request.getPathInfo() != null) {
        String cleanpath = request.getPathInfo().replaceFirst("^/", "").replaceFirst("/$", "").replaceAll("\\/+", "/");
        List<String> path = new ArrayList<String>(Arrays.asList(cleanpath.split("\\/", -1)));
        if (path.isEmpty() || path.get(0).equals("")) {
          response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed process ID.");
          return;
        }
        Queue.Queueable<?> process = queue.getProcess(path.get(0));
        if (process == null) {
          response.sendError(HttpServletResponse.SC_NOT_FOUND, "Process id=" + path.get(0) + " not found.");
          return;
        }
        response.getWriter().write(process.asJSON());
      } else {
        response.getWriter().write(queue.asJSON());
      }
    }

  }

  public static Queue create(ServletContext context) {
    return new Queue(context);
  }

  public String asJSON() {
    try {
      return Queue.serializers.COMPACT.toJson(queue.values(), new TypeToken<Collection<Queueable<?>>>() {}.getType());
    } catch (Exception e) {
      return "{}";
    }
  }

  public Queueable<?> getProcess(String processId) {
    return queue.get(processId);
  }

  public static final String SERVLET_REF = "Queue";
  private long index = System.currentTimeMillis();
  private static final String DEFAULT_POOL_ID = "Queue" + Queue.class.hashCode();
  private Map<String, Queueable<?>> queue = Collections.synchronizedMap(new TreeMap<String, Queueable<?>>(new Comparator<String>() {

    @Override
    public int compare(String s1, String s2) {
      return (s2 == null ? "" : s2).compareTo(s1 == null ? "" : s1);
    }
  }));
  private Map<String, ExecutorService> threadpool = new HashMap<String, ExecutorService>();
  private int defaultPoolsize = Math.max((int) Math.floor(Runtime.getRuntime().availableProcessors() / 2.), 1);

  public Queue(ServletContext context) {
    super(SERVLET_REF);
    context.setAttribute(SERVLET_REF, this);
    setDelay(15, TimeUnit.SECONDS);
    setRunning(true);
  }

  public int getDefaultPoolsize() {
    return defaultPoolsize;
  }

  public void setDefaultPoolsize(int defaultPoolsize) {
    this.defaultPoolsize = defaultPoolsize;
  }

  public ExecutorService createPool(String name, int poolsize) {
    ExecutorService executor = threadpool.get(name);
    return executor == null ? threadpool.put(name, executor = Executors.newFixedThreadPool(poolsize)) : executor;
  }

  public void destroyPool(String name) {
    ExecutorService executor = threadpool.remove(name);
    if (executor != null) {
      executor.shutdownNow();
      try {
        executor.awaitTermination(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {

      }
    }
  }

  private static String hostname = getHostname();

  private static String getHostname() {
    String host = UUID.randomUUID().toString().toUpperCase().replaceAll("[^0-9A-Z]", "");
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {

    }
    return host;
  }

  private String nextId() {
    synchronized (queue) {
      return ("PID" + hostname + (index++)).replaceAll("[^A-Za-z0-9]", "");
    }
  }

  public <V> V process(final Queueable<V> process, String poolID) throws InterruptedException, ExecutionException {
    return submit(process, poolID).get();
  }

  public <V> V process(final Queueable<V> process) throws InterruptedException, ExecutionException {
    return submit(process).get();
  }

  public void processAll(final List<Queueable<?>> processes, int threads) throws InterruptedException, ExecutionException {
    String poolId = nextId();
    ExecutorService pool = createPool(poolId, threads);
    for (Queueable<?> process : processes) {
      submit(process, poolId);
    }
    pool.shutdown();
    pool.awaitTermination(24, TimeUnit.HOURS);
    threadpool.remove(poolId);
    pool = null;
  }

  public <V> Future<V> submit(Queueable<V> process) throws InterruptedException, ExecutionException {
    return this.submit(process, DEFAULT_POOL_ID);
  }

  public <V> Future<V> submit(Queueable<V> process, String poolId) throws InterruptedException, ExecutionException {
    process.setProcessId(nextId());
    process.setQueueId(poolId);
    process.times.submitted = System.currentTimeMillis();
    queue.put(process.getProcessId(), process);
    FutureTask<V> future = new FutureTask<V>(process);
    ExecutorService pool = threadpool.get(poolId);
    if (pool == null) {
      threadpool.put(poolId, pool = Executors.newFixedThreadPool(defaultPoolsize));
    }
    pool.submit(future);
    return future;
  }

  @Override
  protected void onInterval(int interval) {
    long now = System.currentTimeMillis();
    for (Queueable<?> process : queue.values().toArray(new Queueable<?>[0])) {
      if (process.state == State.COMPLETE) {
        if (now - process.times.end > process.times.expiration) {
          queue.remove(process.getProcessId());
        }
      }
    }
  }

  public enum State {
    WORKING, QUEUED, COMPLETE
  }

  public static abstract class Queueable<R> implements Callable<R> {

    public String asJSON() {
      return Queue.serializers.DEFAULT.toJson(this, new TypeToken<Queueable<?>>() {}.getType());
    }

    private String processId;
    private String queueId;
    private String title = "-";
    private String description = "-";
    private String action = "-";
    private String info = "-";
    private float percent = 0;
    private transient MemoryHandler handler = new MemoryHandler();
    private int warnings = 0;
    private int errors = 0;
    private State state = State.QUEUED;
    private Times times = new Times();
    private transient CountingStream stream;
    private transient long total;
    private transient Timer timer = new Timer(20, new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent ae) {
        if (stream != null) {
          setPercent(stream.getCount() * 1f / total * 1f);
          if (total == stream.getCount()) {
            clearCount();
          }
        }
      }
    });

    public InputStream count(InputStream is, long length) {
      total = length;
      stream = new CountingInputStream(is);
      timer.restart();
      return (InputStream) stream;
    }

    public OutputStream count(OutputStream os, long length) {
      total = length;
      stream = new CountingOutputStream(os);
      timer.restart();
      return (OutputStream) stream;
    }

    public void clearCount() {
      if (timer.isRunning()) {
        timer.stop();
      }
      stream = null;
    }

    public static class Times {
      public long submitted = 0;
      public long start = 0;
      public long end = 0;
      public long expiration = 5 * 1000 * 60;
    }

    public String getAction() {
      return action;
    }

    public Queueable<R> setAction(String action) {
      this.action = action;
      return this;
    }

    public String getInfo() {
      return info;
    }

    public Queueable<R> setInfo(String info) {
      this.info = info;
      return this;
    }

    public double getPercent() {
      return percent;
    }

    public Queueable<R> setPercent(float percent) {
      this.percent = percent;
      return this;
    }

    public String getTitle() {
      return title;
    }

    public Queueable<R> setTitle(String title) {
      this.title = title;
      return this;
    }

    public String getDescription() {
      return description;
    }

    public Queueable<R> setDescription(String description) {
      this.description = description;
      return this;
    }

    public int getWarnings() {
      return warnings;
    }

    public int getErrors() {
      return errors;
    }

    public State getState() {
      return state;
    }

    public String getProcessId() {
      return processId;
    }

    public String getQueueId() {
      return queueId;
    }

    public Queueable<R> setExpiration(long expiration) {
      times.expiration = expiration;
      return this;
    }

    public long getExpiration() {
      return times.expiration;
    }

    private void setProcessId(String processId) {
      this.processId = processId;
    }

    private void setQueueId(String queueId) {
      this.queueId = queueId;
    }

    public abstract R work();

    @Override
    public R call() throws Exception {
      this.getLogger().addHandler(handler);
      this.state = State.WORKING;
      times.start = System.currentTimeMillis();
      R res = null;
      try {
        res = work();
      } catch (Exception e) {
        this.getLogger().log(Level.SEVERE, null, e);
      }
      this.state = State.COMPLETE;
      times.end = System.currentTimeMillis();
      this.getLogger().removeHandler(handler);
      return res;
    }

    private transient static SimpleFormatter formatter = new SimpleFormatter();

    protected static class LoggerEntry {
      protected String id;
      // protected String formatted;
      protected String message;
      // protected long time;
      // protected String loggername;
      // protected Level level;

      protected LoggerEntry(LogRecord record) {
        id = "LOG" + record.getSequenceNumber();
        message = formatter.format(record).replaceAll("\r", "").trim();
        // Object[] params = record.getParameters();
        // if ((params != null) && (params.length > 0) && (record.getMessage().indexOf("{0") >= 0))
        // {
        // message = MessageFormat.format(record.getMessage(), params);
        // }
        // time = record.getMillis();
        // loggername = record.getLoggerName();
        // level = record.getLevel();
      }
    }

    private List<LoggerEntry> logs = new LinkedList<LoggerEntry>();

    private class MemoryHandler extends Handler {

      @Override
      public void close() throws SecurityException {

      }

      @Override
      public void flush() {

      }

      @Override
      public void publish(LogRecord record) {
        errors += record.getLevel() == Level.SEVERE ? 1 : 0;
        warnings += record.getLevel() == Level.WARNING ? 1 : 0;
        logs.add(new LoggerEntry(record));
      }

    }

    private transient Logger logger;

    public Logger getLogger() {
      return logger == null ? (this.logger = Logger.getLogger(processId)) : logger;
    }

    private static interface CountingStream {

      public long getCount();

    }

    private static final class CountingOutputStream extends FilterOutputStream implements CountingStream {

      private long count;

      /**
       * Wraps another output stream, counting the number of bytes written.
       *
       * @param out the output stream to be wrapped
       */
      public CountingOutputStream(OutputStream out) {
        super(out);
      }

      /** Returns the number of bytes written. */
      public long getCount() {
        return count;
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        count += len;
      }

      @Override
      public void write(int b) throws IOException {
        out.write(b);
        count++;
      }
    }

    private static final class CountingInputStream extends FilterInputStream implements CountingStream {

      private long count;
      private long mark = -1;

      /**
       * Wraps another input stream, counting the number of bytes read.
       *
       * @param in the input stream to be wrapped
       */
      public CountingInputStream(InputStream in) {
        super(in);
      }

      /** Returns the number of bytes read. */
      public long getCount() {
        return count;
      }

      @Override
      public int read() throws IOException {
        int result = in.read();
        if (result != -1) {
          count++;
        }
        return result;
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        int result = in.read(b, off, len);
        if (result != -1) {
          count += result;
        }
        return result;
      }

      @Override
      public long skip(long n) throws IOException {
        long result = in.skip(n);
        count += result;
        return result;
      }

      @Override
      public synchronized void mark(int readlimit) {
        in.mark(readlimit);
        mark = count;
        // it's okay to mark even if mark isn't supported, as reset won't work
      }

      @Override
      public synchronized void reset() throws IOException {
        if (!in.markSupported()) {
          throw new IOException("Mark not supported");
        }
        if (mark == -1) {
          throw new IOException("Mark not set");
        }

        in.reset();
        count = mark;
      }
    }

  }

  @Override
  protected boolean onStart() {
    return true;
  }

  @Override
  protected boolean onStop() {
    for (String name : threadpool.keySet()) {
      destroyPool(name);
    }
    return true;
  }

  @Override
  protected void onStateChange(ServiceState state) {
    // Not Required
  }



}
