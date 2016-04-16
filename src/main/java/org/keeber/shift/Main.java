package org.keeber.shift;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.annotation.WebListener;

import org.keeber.service.Constants;
import org.keeber.service.Webdaemon;
import org.keeber.service.logging.MemoryLogger;
import org.keeber.service.process.ChannelManager;
import org.keeber.service.process.Queue;
import org.keeber.simpleio.File;
import org.keeber.simpleio.plugin.FtpPlugin;
import org.keeber.simpleio.plugin.SftpPlugin;
import org.keeber.simpleio.plugin.SmbPlugin;

@WebListener
public class Main extends Webdaemon {
  public static final String MAIN_QUEUE="Main";
  private MemoryLogger logger;
  private Queue queue;
  private ChannelManager manager;

  public Main() {
    super(Constants.getServicename());
    super.setDelay(10, TimeUnit.MINUTES);
    File.addPlugin(SmbPlugin.create());
    File.addPlugin(FtpPlugin.create());
    File.addPlugin(SftpPlugin.create());
  }

  @Override
  protected void onInterval(int interval) {
    System.gc();
  } 

  @Override
  protected void onStart(ServletContext context) {
    logger = MemoryLogger.create(context);
    Logger.getLogger("").addHandler(logger.addHandler("Global"));
    queue = Queue.create(context);
    // Create default queue
    queue.createPool(MAIN_QUEUE, 5);

    try {
      File home = Constants.getHome(context);
      getLogger().log(Level.INFO, "[{0}] Home[{1}]", new Object[] {name, home.getPath()});
      File conf = Constants.getConf(home);
      getLogger().log(Level.INFO, "[{0}] Conf[{1}]", new Object[] {name, conf.getPath()});

      // Channels
      manager = new ChannelManager(context, conf.create("channels/"));
      // Add logger
      // manager.getLogger().addHandler(logger.addHandler(ChannelManager.SERVLET_REF));
      // Add channel group
      manager.addChannelGroup("main", "Shift-Hotfolders", Shiftfolder.class);
      manager.setRunning(true);


    } catch (IOException e) {
      getLogger().log(Level.INFO, "[" + name + "] Error loading application home.", e);
    }

  }

  @Override
  protected void onStop(ServletContext context) {
    manager.setRunning(false);
    queue.dispose();
  }

}
