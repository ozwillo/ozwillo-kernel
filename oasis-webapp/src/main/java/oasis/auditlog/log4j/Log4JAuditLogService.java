package oasis.auditlog.log4j;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.Message;

import com.google.common.collect.ImmutableMap;

import oasis.auditlog.AuditLogEvent;
import oasis.auditlog.AuditLogService;
import oasis.auditlog.JsonMessage;

public class Log4JAuditLogService extends AuditLogService {
  private static final String LOGGER_NAME = "OASIS_AUDIT_LOGGER";

  private Logger auditLogger = LogManager.getLogger(LOGGER_NAME);

  @Override
  protected void log(AuditLogEvent auditLogEvent) {
    ImmutableMap<String, Object> data = ImmutableMap.<String, Object>of(
        "type", auditLogEvent.getEventType(),
        "time", auditLogEvent.getDate(),
        "data", auditLogEvent.getContextMap()
    );

    Message message = new JsonMessage(data, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"), TimeZone.getTimeZone(
        "UTC"));

    auditLogger.info(message);
  }
}
