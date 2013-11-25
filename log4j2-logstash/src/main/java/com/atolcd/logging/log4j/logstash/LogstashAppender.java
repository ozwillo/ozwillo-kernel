package com.atolcd.logging.log4j.logstash;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

@Plugin(name = "Logstash", category = "Core", elementType = "appender", printObject = false)
public class LogstashAppender extends AbstractAppender {

  private final LogstashManager manager;

  private LogstashAppender(String name, Layout layout, Filter filter, LogstashManager manager, boolean ignoreExceptions) {
    super(name, filter, layout, ignoreExceptions);

    this.manager = manager;
  }

  public static LogstashAppender createAppender(String name, String host, int port) {
    return createAppender(name, host, port, false, null, null);
  }

  public static LogstashAppender createAppender(String name, String host, int port, boolean ignoreExceptions, Layout layout, Filter filter) {
    if (name == null) {
      LOGGER.error("No name provided for LogstashAppender");
      return null;
    }

    if (host == null || host.isEmpty()) {
      LOGGER.error("The hostname can't be null.");
      return null;
    }

    LogstashManager manager = LogstashManager.getLogstashManager(name, host, port);
    if (manager == null) {
      LOGGER.error("The manager can't be null.");
      return null;
    }

    if (layout == null) {
      layout = PatternLayout.createLayout(null, null, null, null, null);
    }

    return new LogstashAppender(name, layout, filter, manager, ignoreExceptions);
  }

  @PluginFactory
  public static LogstashAppender createAppender(@PluginAttribute("name") String name,
      @PluginAttribute("host") String host,
      @PluginAttribute("port") String port,
      @PluginAttribute("ignoreExceptions") String ignore,
      @PluginElement("Layout") Layout layout,
      @PluginElement("Filters") Filter filter) {
    boolean ignoreExceptions = Boolean.parseBoolean(ignore);

    if (port == null || port.isEmpty()) {
      LOGGER.error("The logstash port can't be null.");
      return null;
    }
    int portInteger;
    try {
      portInteger = Integer.parseInt(port);
    } catch (NumberFormatException e) {
      LOGGER.error("The logstash port is not a number.");
      return null;
    }

    return createAppender(name, host, portInteger, ignoreExceptions, layout, filter);
  }

  @Override
  public void append(LogEvent logEvent) {
    String jsonMessage = logEvent.getMessage().getFormattedMessage();
    this.manager.recordEvent(jsonMessage);
  }
}
