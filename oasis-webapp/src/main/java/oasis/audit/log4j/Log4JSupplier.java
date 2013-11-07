package oasis.audit.log4j;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.message.Message;

import oasis.audit.LogEvent;

public interface Log4JSupplier {
  public Message generateMessage(LogEvent logEvent);

  public Appender createAppender(String appenderName);
}
