package oasis.auditlog;

import java.text.DateFormat;
import java.util.TimeZone;

import org.apache.logging.log4j.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;

public class JsonMessage implements Message {
  private static final Logger logger = LoggerFactory.getLogger(JsonMessage.class);
  private final Object object;
  private final DateFormat dateFormat;
  private final TimeZone timeZone;

  public JsonMessage(Object object) {
    this(object, null, null);
  }

  public JsonMessage(Object object, DateFormat dateFormat, TimeZone timeZone) {
    this.object = object;
    this.dateFormat = dateFormat;
    this.timeZone = timeZone;
  }

  @Override
  public String getFormattedMessage() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JodaModule());
    if (dateFormat != null) {
      objectMapper.setDateFormat(dateFormat);
    }
    if (timeZone != null) {
      objectMapper.setTimeZone(timeZone);
    }

    try {
      return objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      logger.error("Error during the transformation of the AuditLogEvent into a JSON string.", e);
    }
    return null;
  }

  @Override
  public String getFormat() {
    return null;
  }

  @Override
  public Object[] getParameters() {
    return new Object[]{object};
  }

  @Override
  public Throwable getThrowable() {
    return null;
  }
}
