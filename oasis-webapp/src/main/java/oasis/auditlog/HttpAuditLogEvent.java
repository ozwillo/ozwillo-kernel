/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class HttpAuditLogEvent extends AuditLogEvent {
  private static final Logger logger = LoggerFactory.getLogger(HttpAuditLogEvent.class);
  private static final String TYPE = "http_request";
  private static final String URL = "url";
  private static final String METHOD = "method";
  private static final String HEADERS = "headers";
  private static final String STATUS = "status";
  private static final String START_TIME = "start_time";
  private static final String END_TIME = "end_time";
  private static final String ELAPSED_TIME = "elapsed_time";
  private static final String AUTHENTICATION_SCHEME = "auth_scheme";
  private static final String REMOTE_USER = "remote_user";
  private static final String REMOTE_CLIENT = "remote_client";

  public HttpAuditLogEvent() {
    super(TYPE);
  }

  public HttpAuditLogEvent setUrl(String url) {
    this.addContextData(URL, url);
    return this;
  }

  public HttpAuditLogEvent setMethod(String method) {
    this.addContextData(METHOD, method);
    return this;
  }

  public HttpAuditLogEvent setHeaders(ImmutableMap<String, Object> headers) {
    this.addContextData(HEADERS, headers);
    return this;
  }

  public HttpAuditLogEvent setStatus(int status) {
    this.addContextData(STATUS, status);
    return this;
  }

  public HttpAuditLogEvent setStartTime(@Nullable Long startTime) {
    if (startTime != null) {
      this.addContextData(START_TIME, startTime);
    }
    return this;
  }

  public HttpAuditLogEvent setEndTime(@Nullable Long endTime) {
    if (endTime != null) {
      this.addContextData(END_TIME, endTime);
    }
    return this;
  }

  public HttpAuditLogEvent setElapsedTime(@Nullable Long duration) {
    if (duration != null) {
      this.addContextData(ELAPSED_TIME, duration);
    }
    return this;
  }

  public HttpAuditLogEvent setAuthenticationScheme(@Nullable String authScheme) {
    if (authScheme != null) {
      this.addContextData(AUTHENTICATION_SCHEME, authScheme);
    }
    return this;
  }

  public HttpAuditLogEvent setRemoteUser(@Nullable String remoteUser) {
    if (remoteUser != null) {
      this.addContextData(REMOTE_USER, remoteUser);
    }
    return this;
  }

  public HttpAuditLogEvent setRemoteClient(@Nullable String remoteClient) {
    if (remoteClient != null) {
      this.addContextData(REMOTE_CLIENT, remoteClient);
    }
    return this;
  }

  @Override
  protected boolean checkBeforeBuild() {
    for (String key : ImmutableList.of(URL, METHOD, HEADERS, STATUS)) {
      if (!this.getContextMap().containsKey(key)) {
        logger.error("Key {} missing in the log event of type {}", key, this.getClass().getName());
        return false;
      }
    }
    return true;
  }
}
