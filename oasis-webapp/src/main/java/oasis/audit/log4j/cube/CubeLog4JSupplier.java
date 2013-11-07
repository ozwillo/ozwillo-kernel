package oasis.audit.log4j.cube;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

import javax.inject.Inject;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.message.Message;

import com.atolcd.logging.log4j.cube.CubeAppender;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import oasis.audit.LogEvent;
import oasis.audit.JsonMessage;
import oasis.audit.log4j.Log4JSupplier;
import oasis.web.Settings;

public class CubeLog4JSupplier implements Log4JSupplier {

  private final Settings settings;

  @Inject
  public CubeLog4JSupplier(Settings settings) {
    this.settings = settings;
  }

  @Override
  public Message generateMessage(LogEvent logEvent) {
    ImmutableList<Object> data = ImmutableList.<Object>of(ImmutableMap.<String, Object>of(
        "type", logEvent.getEventType(),
        "time", logEvent.getDate(),
        "data", logEvent.getContextMap()
    ));

    return new JsonMessage(data, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"), TimeZone.getTimeZone("UTC"));
  }

  @Override
  public Appender createAppender(String appenderName) {
    return CubeAppender.createAppender(appenderName, settings.auditCubeUrl, null, null, null);
  }
}
