package oasis.auditlog.log4j.cube;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

import javax.inject.Inject;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.message.Message;

import com.atolcd.logging.log4j.cube.CubeAppender;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import oasis.auditlog.AuditLogEvent;
import oasis.auditlog.JsonMessage;
import oasis.auditlog.log4j.Log4JSupplier;

public class CubeLog4JSupplier implements Log4JSupplier {

  private final CubeLog4JAuditLogModule.Settings settings;

  @Inject
  public CubeLog4JSupplier(CubeLog4JAuditLogModule.Settings settings) {
    this.settings = settings;
  }

  @Override
  public Message generateMessage(AuditLogEvent auditLogEvent) {
    ImmutableList<Object> data = ImmutableList.<Object>of(ImmutableMap.<String, Object>of(
        "type", auditLogEvent.getEventType(),
        "time", auditLogEvent.getDate(),
        "data", auditLogEvent.getContextMap()
    ));

    return new JsonMessage(data, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"), TimeZone.getTimeZone("UTC"));
  }

  @Override
  public Appender createAppender(String appenderName) {
    return CubeAppender.createAppender(appenderName, settings.cubeUrl, null, null, null);
  }
}
