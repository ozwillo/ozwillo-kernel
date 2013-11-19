package oasis.audit.log4j.fluentd;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

import javax.inject.Inject;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.message.Message;

import com.atolcd.logging.log4j.fluentd.FluentdAppender;
import com.google.common.collect.ImmutableMap;

import oasis.audit.JsonMessage;
import oasis.audit.LogEvent;
import oasis.audit.log4j.Log4JSupplier;

public class FluentdLog4JSupplier implements Log4JSupplier {
  private final FluentdLog4JAuditModule.Settings settings;

  @Inject
  public FluentdLog4JSupplier(FluentdLog4JAuditModule.Settings settings) {
    this.settings = settings;
  }

  @Override
  public Message generateMessage(LogEvent logEvent) {
    ImmutableMap<String, Object> data = ImmutableMap.<String, Object>of(
        "type", logEvent.getEventType(),
        "time", logEvent.getDate(),
        "data", logEvent.getContextMap()
    );

    return new JsonMessage(data, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"), TimeZone.getTimeZone("UTC"));
  }

  @Override
  public Appender createAppender(String appenderName) {
    return FluentdAppender.createAppender(appenderName, settings.fluentdUrl, settings.fluentdTag, null, null, null);
  }
}
