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
package oasis.auditlog.log4j;

import java.text.SimpleDateFormat;
import java.util.Locale;
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

    Message message = new JsonMessage(data, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT), TimeZone.getTimeZone("UTC"));

    auditLogger.info(message);
  }
}
