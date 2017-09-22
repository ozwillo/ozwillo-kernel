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

import java.time.Instant;

import com.google.common.collect.ImmutableMap;

public abstract class AuditLogEvent {
  private final String eventType;
  private final ImmutableMap.Builder<String, Object> contextMapBuilder;
  private final Instant date;
  private ImmutableMap<String, Object> contextMap;
  private AuditLogService auditLogService;

  public AuditLogEvent(String eventType) {
    this(eventType, Instant.now());
  }

  public AuditLogEvent(String eventType, Instant date) {
    this.eventType = eventType;
    this.contextMapBuilder = ImmutableMap.builder();

    this.date = date;
  }

  public String getEventType() {
    return eventType;
  }

  public Instant getDate() {
    return date;
  }

  protected final void setAuditLogService(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  public ImmutableMap<String, Object> getContextMap() {
    if (contextMap == null) {
      contextMap = contextMapBuilder.build();
    }
    return contextMap;
  }

  protected void addContextData(String key, Object value) {
    contextMapBuilder.put(key, value);
  }

  protected boolean checkBeforeBuild() {
    return true;
  }

  /**
   * Send the event to the logger
   */
  public void log() {
    if (checkBeforeBuild()) {
      auditLogService.log(this);
    }
  }
}
