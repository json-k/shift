package org.keeber.shift;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.keeber.lang.Maps;
import org.keeber.lang.Stringmatic;
import org.keeber.lang.Stringmatic.EvaluationException;
import org.keeber.lang.Strings;
import org.keeber.service.process.Channel;
import org.keeber.service.process.Queue;
import org.keeber.shift.Shiftfolder.Configuration.Output;
import org.keeber.shift.Shiftfolder.Configuration.Output.Location;
import org.keeber.simpleio.File;
import org.keeber.simpleio.Hotfolder;

public class Shiftfolder extends Channel<Shiftfolder.Configuration> {

  private enum PathType {
    FILE, SMB, FTP, SFTP;
  }

  protected static class Configuration {
    protected Scan scan = new Scan();
    protected List<Output> outputs = new LinkedList<>();
    protected boolean verbose = false;

    protected static class Scan {
      protected String path;
      protected int time = 10;
      protected int settle = 2;
      protected TimeUnit unit = TimeUnit.SECONDS;
      protected String pattern = ".*";
    }

    protected static class Output {
      protected String name;
      protected String value = "${file.name}";
      protected String pattern = ".*";
      protected List<Location> locations = new LinkedList<>();

      protected static class Location {
        protected PathType type = PathType.FILE;
        protected String username, hostname, password, path, filename;
      }
    }
  }

  private transient Hotfolder hotfolder = null;
  private transient AtomicInteger index = new AtomicInteger();

  @Override
  public void onNew() {
    setConfiguration(new Configuration());
  }

  @Override
  public void onDestroy() {
    stopFolder();
  }

  private void stopFolder() {
    if (hotfolder != null) {
      for (Handler handler : hotfolder.getLogger().getHandlers()) {
        hotfolder.getLogger().removeHandler(handler);
      }
      hotfolder.reset();
      hotfolder.setRunning(false);
      hotfolder = null;
    }
  }

  @Override
  public void onUpdate() {
    stopFolder();
    if (isEnabled()) {
      Configuration conf = getConfiguration();
      hotfolder = new Hotfolder(getId());
      hotfolder.setUnit(conf.scan.unit);
      hotfolder.setInterval(conf.scan.time);
      hotfolder.setSettle(conf.scan.settle);
      hotfolder.setFilters(f -> f.isFile() && f.isVisible()&& f.getPath().matches(conf.scan.pattern), File.filters.VISIBLE_DIRECTORIES);
      try {
        hotfolder.setFolder(File.resolve(conf.scan.path));
      } catch (IOException e) {
        getLogger().log(Level.SEVERE, "[" + getName() + "] Hotfolder startup error.", e);
      }
      for (Handler handler : getLogger().getHandlers()) {
        hotfolder.getLogger().addHandler(handler);
      }
      hotfolder.setSubscriber(new Hotfolder.Subscriber() {

        @Override
        public void onAdded(File file) {
          Queue queue = (Queue) getContext().getAttribute(Queue.SERVLET_REF);
          try {
            String description = Strings.join(" ", Strings.formatBytes(file.length(), true), file.parent().getPath());
            queue.submit(new Queue.Queueable<Boolean>() {

              @Override
              public Boolean work() {
                try {
                  Configuration conf = getConfiguration();
                  Stringmatic matic = new Stringmatic();
                  matic.put("file", Maps.from("name", file.getName(), "fullpath", file.getPath(), "modified", file.getLastModified(), "basename", file.getBaseName(), "ext", file.getExtension(), "length", file.length(),
                      "path", file.parent().getPath(), "relpath", file.parent().getPath().replace(getParent().getFolder().getPath(), "/")));
                  matic.put("index", index);
                  setAction("Creating paths");
                  Matcher matcher;
                  List<String> paths = new ArrayList<>();
                  int n;
                  for (Output output : conf.outputs) {
                    try {
                      getLogger().log(Level.INFO, "[{0}] Evaluating [pattern:{1}][value:{2}][resolved:{3}]", new Object[] {output.name, output.pattern, output.value, matic.evaluate(output.value)});
                      matcher = Pattern.compile(output.pattern).matcher(matic.evaluate(output.value));
                      if (matcher.matches()) {
                        matic.put("match", matcher);
                        getLogger().log(Level.INFO, "[{0}] Matched" + (conf.verbose ? "\n[vars:{1}]" : ""), new Object[] {output.name, matic.asJson()});
                        n = 0;
                        for (Location location : output.locations) {
                          n++;
                          StringBuilder path = Strings.build();
                          String password = "";
                          if (location.type != Shiftfolder.PathType.FILE) {
                            path.append(location.type.toString().toLowerCase()).append("://");
                            password = URLEncoder.encode(location.password, "UTF-8");
                            String auth = Strings.join(":", location.username, password);
                            path.append(auth).append(Strings.functionallyEmpty(auth) ? "" : "@").append(matic.evaluate(location.hostname));
                          }
                          path.append(matic.evaluate(location.path)).append(matic.evaluate(location.filename));
                          paths.add(path.toString());
                          getLogger().log(Level.INFO, "[{0}][Location-{1}]\n\tPath:[{2}]", new Object[] {output.name, n, Strings.functionallyEmpty(password) ? path.toString() : path.toString().replace(password, "*")});
                        }
                      }
                    } catch (EvaluationException e) {
                      getLogger().log(Level.WARNING, "[" + output.name + "] Error evaluating pattern / value,", e);
                    }
                  }
                  n = 1;
                  setAction("Copying");
                  for (String path : paths) {
                    try {
                      File output = File.resolve(path);
                      setInfo("[" + n + "-" + paths.size() + "] " + output.getPath());
                      getLogger().log(Level.INFO, "[{0}][{1}][{2}]Copying...", new Object[] {getName(), n, output.getPath()});
                      if (!output.parent().exists()) {
                        try {
                          output.parent().mkdirs();
                        } catch (IOException e) {
                          getLogger().log(Level.WARNING, "Possible error creating directory...");
                        }
                      }
                      Streams.copy(count(file.open(File.READ), file.length()), output.open(File.WRITE), true);
                      output.dispose();
                      clearCount();
                      setPercent(1);
                    } catch (IOException e) {
                      getLogger().log(Level.SEVERE, "IO Error", e);
                    }
                    n++;
                  }
                  setAction("-");
                  setInfo("-");
                  if (getErrors() == 0 && paths.size() > 0) {
                    file.delete();
                  }
                } catch (IOException e) {
                  getLogger().log(Level.SEVERE, "IO Error", e);
                } finally {
                  release(file);
                  setExpiration(1000 + (1000 * 60 * getErrors()));
                }
                return true;
              }
            }.setTitle(file.getName()).setDescription(description), Main.MAIN_QUEUE);
          } catch (InterruptedException e) {
            getLogger().log(Level.SEVERE, null, e);
          } catch (ExecutionException e) {
            getLogger().log(Level.SEVERE, null, e);
          } catch (IOException e) {
            getLogger().log(Level.SEVERE, null, e);
          }

        }
      });
      hotfolder.setRunning(true);
    }


  }

  @Override
  public void onService(HttpServletRequest request, HttpServletResponse response, List<String> path) {
    // This is not used in this implementation but could be easily addapted to take files as HTTP
    // requests.
  }

}
