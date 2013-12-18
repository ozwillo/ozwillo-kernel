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
