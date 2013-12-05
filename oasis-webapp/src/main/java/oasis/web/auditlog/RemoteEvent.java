package oasis.web.auditlog;

import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
  ObjectNode log;

  // For swagger
  public Instant getTime() {
    return time;
  }

  // For swagger
  public ObjectNode getLog() {
    return log;
  }
}
