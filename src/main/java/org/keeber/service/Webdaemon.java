
package org.keeber.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;


public abstract class Webdaemon implements ServletContextListener {
	public static String HOSTNAME;
	static {
		try {
			HOSTNAME = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
		}
	}
	private TimeUnit timeUnit = TimeUnit.MINUTES;

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

	protected String name;
	
	public Webdaemon(String name){
		this.name=name;
	}
	
	public String getName(){
		return name;
	}
	
	private ScheduledExecutorService scheduler;
	private int interval;
	private boolean running = false;

	public boolean isRunning() {
		return running;
	}

	protected void setRunning(boolean running, ServletContext context) {
		setRunning(running, true, context);
	}

	private void setRunning(boolean running, boolean events,
			ServletContext context) {
		if (this.running != running) {
			this.running = running;
			if (running) {
				interval = 0;
				scheduler = Executors.newSingleThreadScheduledExecutor();
				if (events) {
					onStart(context);
				}
				scheduler.scheduleWithFixedDelay(new Runnable() {

					public void run() {
						try {
							onInterval(interval++);
						} catch (Exception ex) {
							Logger.getLogger(Webdaemon.class.getName()).log(
									Level.SEVERE, null, ex);
						}
					}
				}, 0, delay, timeUnit);
			} else {
				scheduler.shutdownNow();
				try {
					scheduler.awaitTermination(5, TimeUnit.SECONDS);
				} catch (InterruptedException ex) {

				}
				if (events) {
					onStop(context);
				}
			}
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		setRunning(false, sce.getServletContext());
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		setRunning(true, sce.getServletContext());
	}

	protected abstract void onInterval(int interval);

	protected abstract void onStart(ServletContext context);

	protected abstract void onStop(ServletContext context);

	private void update() {
		if (isRunning()) {
			setRunning(false, false, null);
			setRunning(true, false, null);
		} 
	}
	
	private transient Logger logger;
	
    public Logger getLogger(){
    	return logger==null?this.logger=Logger.getLogger(this.getClass().getName()):logger;
    }
}
