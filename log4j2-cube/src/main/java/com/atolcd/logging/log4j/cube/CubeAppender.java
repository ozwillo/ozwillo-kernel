package com.atolcd.logging.log4j.cube;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import com.google.common.base.Strings;

@Plugin(name = "Cube", category = "Core", elementType = "appender", printObject = false)
public final class CubeAppender extends AbstractAppender {

  private final CubeManager manager;

  private CubeAppender(String name, Layout layout, Filter filter, CubeManager manager, boolean ignoreExceptions) {
    super(name, filter, layout, ignoreExceptions);

    this.manager = manager;
  }

  @PluginFactory
  public static CubeAppender createAppender(@PluginAttribute("name") String name,
      @PluginAttribute("url") String url,
      @PluginAttribute("ignoreExceptions") String ignore,
      @PluginElement("Layout") Layout layout,
      @PluginElement("Filters") Filter filter) {
    boolean ignoreExceptions = Boolean.parseBoolean(ignore);
    if (name == null) {
      LOGGER.error("No name provided for CubeAppender");
      return null;
    }

    if (Strings.isNullOrEmpty(url)) {
      LOGGER.error("The url can't be null.");
      return null;
    }

    if (layout == null) {
      layout = PatternLayout.createLayout(null, null, null, null, null);
    }

    return new CubeAppender(name, layout, filter, CubeManager.getCubeManager(name, url), ignoreExceptions);
  }

  @Override
  public void append(LogEvent logEvent) {
    String jsonMessage = logEvent.getMessage().getFormattedMessage();
    this.manager.recordEvent(jsonMessage);
  }
}
