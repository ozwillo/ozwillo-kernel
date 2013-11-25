package com.atolcd.logging.log4j.logstash;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.ErrorManager;

import org.apache.logging.log4j.core.appender.AbstractManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogstashManager extends AbstractManager {
  private static final Logger logger = LoggerFactory.getLogger(LogstashManager.class);
  private static final byte[] LF = "\n".getBytes(StandardCharsets.UTF_8);

  private final String host;
  private final int port;

  private LogstashManager(String name, String host, int port) {
    super(name);

    this.host = host;
    this.port = port;
  }

  public static LogstashManager getLogstashManager(String name, String host, int port) {
    return new LogstashManager(name, host, port);
  }

  public void recordEvent(String json) {
    // TODO: handle connection pool
    try (Socket socket = new Socket(host, port)) {
      socket.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
      socket.getOutputStream().write(LF);
    } catch (Exception e) {
      logger.error("The audit event can't reach the logstash server", e);
    }
  }
}
