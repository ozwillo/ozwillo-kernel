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
import java.util.Map;

public class RemoteAuditLogEvent extends AuditLogEvent {
  private static final String TYPE = "remote";

  public RemoteAuditLogEvent(Instant date) {
    super(TYPE, date);
  }

  public RemoteAuditLogEvent setLog(Map<String, Object> log) {
    // XXX: handle  mandatory fields ?

    for (Map.Entry<String, Object> entry : log.entrySet()) {
      this.addContextData(entry.getKey(), entry.getValue());
    }

    return this;
  }

}
