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
package oasis.web.auditlog;

import java.util.Map;

import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

/*
 * Extracted from {@link AuditLogEndpoint} for swagger.
 */
@ApiModel
class RemoteEvent {
  @JsonProperty()
  @ApiModelProperty(required = true)
  Instant time;

  @JsonProperty()
  @ApiModelProperty(required = true)
  Map<String, Object> log;

  // For swagger
  public Instant getTime() {
    return time;
  }

  // For swagger
  public Map<String, Object> getLog() {
    return log;
  }
}
