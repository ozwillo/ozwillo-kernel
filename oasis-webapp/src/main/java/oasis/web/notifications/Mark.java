package oasis.web.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

/*
 * Extracted from {@link NotificationEndpoint} for swagger.
 */
@ApiModel
class Mark {

  @JsonProperty
  @ApiModelProperty
  String[] messageIds;

  @JsonProperty
  @ApiModelProperty
  String status;

  // For swagger
  public String[] getMessageIds() {
    return messageIds;
  }

  // For swagger
  public String getStatus() {
    return status;
  }
}
