package oasis.web.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

/*
 * Extracted from {@link NotificationEndpoint} for swagger.
 */
@ApiModel
class IncomingNotification {

  @JsonProperty
  @ApiModelProperty
  String[] userIds;

  @JsonProperty
  @ApiModelProperty
  String[] groupIds;

  @JsonProperty
  @ApiModelProperty
  String data;

  @JsonProperty
  @ApiModelProperty
  String message;

  // For swagger
  public String[] getUserIds() {
    return userIds;
  }

  // For swagger
  public String[] getGroupIds() {
    return groupIds;
  }

  // For swagger
  public String getData() {
    return data;
  }

  // For swagger
  public String getMessage() {
    return message;
  }
}

