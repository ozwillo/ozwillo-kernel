/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
