package oasis.web.eventbus;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

/*
 * Extracted from {@link EventBusEndpoint} for swagger.
 */
@ApiModel
class Event {

  @JsonProperty()
  @ApiModelProperty
  String message;

  @JsonProperty()
  @ApiModelProperty
  String data;

  @JsonProperty()
  @ApiModelProperty
  String eventType; // Unique (gives the application for an organisation)

  // For swagger
  public String getMessage() {
    return message;
  }

  // For swagger
  public String getData() {
    return data;
  }

  // For swagger
  public String getEventType() {
    return eventType;
  }
}

