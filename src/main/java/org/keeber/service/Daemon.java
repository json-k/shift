
package org.keeber.service;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class Daemon {
  protected String name;
  private transient Logger logger;
  private TimeUnit timeUnit = TimeUnit.MINUTES;
  private transient ExecutorService queue = Executors.newSingleThreadExecutor();

  public enum ServiceState {
    STARTING, RUNNING, STOPPING, STOPPED, FAILED
  }

  private ServiceState state = ServiceState.STOPPED;

  private void setServiceState(ServiceState state) {
    if (this.state != state) {
      this.state = state;
      onStateChange(state);
    }
  }

  public ServiceState getServiceState() {
    return state;
  }

  public Daemon(String name) {
    this.name = name;
    init();
  }

  public Daemon() {
    this.name = getClass().getSimpleName();
    init();
  }

  private void init() {
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

      @Override
      public void run() {
        try {
          dispose();
        } catch (Exception e) {

        }
      }
    }));
  }

  public String getName() {
    return this.name;
  }

  public TimeUnit getTimeUnit() {
    return timeUnit;
  }

  public void setTimeUnit(TimeUnit timeUnit) {
    this.timeUnit = timeUnit;
    update();
  }

  private int delay = 1;

  public void setDelay(int delay, TimeUnit timeUnit) {
    this.delay = delay;
    this.timeUnit = timeUnit;
    update();
  }

  public int getDelay() {
    return delay;
  }

  public void setDelay(int delay) {
    this.delay = delay;
    update();
  }

  private ScheduledExecutorService scheduler;
  private int interval;
  private boolean running = false;

  public boolean isRunning() {
    return running;
  }

  public void restart() {
    setRunning(false);
    setRunning(true);
  }

  public void setRunning(boolean running) {
    setRunning(running, true);
  }

  private void setRunning(boolean setrunning, boolean hasevents) {
    final boolean running = setrunning;
    final boolean events = hasevents;
    queue.submit(new Callable<Object>() {

      @Override
      public Object call() throws Exception {
        if (Daemon.this.running != running) {

          if (running) {
            setServiceState(ServiceState.STARTING);
            interval = 0;
            scheduler = Executors.newSingleThreadScheduledExecutor();
            boolean started = true;
            if (events) {
              started = onStart();
            }
            scheduler.scheduleWithFixedDelay(new Runnable() {

              public void run() {
                try {
                  onInterval(interval++);
                } catch (Exception ex) {
                  Logger.getLogger(Daemon.class.getName()).log(Level.SEVERE, null, ex);
                }
              }
            }, delay, delay, timeUnit);
            setServiceState(started ? ServiceState.RUNNING : ServiceState.FAILED);
          } else {
            setServiceState(ServiceState.STOPPING);
            scheduler.shutdownNow();
            try {
              scheduler.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
            }
            if (events) {
              setServiceState(onStop() ? ServiceState.STOPPED : ServiceState.FAILED);
            } else {
              setServiceState(ServiceState.STOPPED);
            }
          }
          Daemon.this.running = running;
        }
        return null;
      }
    });
  }

  protected abstract void onInterval(int interval);

  protected abstract boolean onStart();

  protected abstract boolean onStop();

  protected abstract void onStateChange(ServiceState state);

  private void update() {
    if (isRunning()) {
      setRunning(false, false);
      setRunning(true, false);
    }
  }

  public Logger getLogger() {
    return logger == null
        ? this.logger = Logger.getLogger(this.getClass().getName() + "-" + getName()) : logger;
  }

  public void dispose() {
    if (queue != null) {
      setRunning(false);
      queue.shutdown();
      try {
        queue.awaitTermination(2, TimeUnit.SECONDS);
      } catch (InterruptedException e) {

      }
      queue = null;
    }

  }

}
