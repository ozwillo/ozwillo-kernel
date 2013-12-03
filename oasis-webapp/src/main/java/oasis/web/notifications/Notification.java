package oasis.web.notifications;

import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

/*
 * Extracted from {@link NotificationEndpoint} for swagger.
 */
@ApiModel
class Notification {

  @JsonProperty
  @ApiModelProperty
  String data;

  @JsonProperty
  @ApiModelProperty
  String message;

  @JsonProperty
  @ApiModelProperty
  Instant time;

  // For swagger
  public String getData() {
    return data;
  }

  // For swagger
  public String getMessage() {
    return message;
  }

  // For swagger
  public Instant getTime() {
    return time;
  }
}

