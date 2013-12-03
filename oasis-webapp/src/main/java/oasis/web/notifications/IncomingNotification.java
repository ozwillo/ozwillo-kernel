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
  Notification notification;

  // For swagger
  public String[] getUserIds() {
    return userIds;
  }

  // For swagger
  public String[] getGroupIds() {
    return groupIds;
  }

  // For swagger
  public Notification getNotification() {
    return notification;
  }
}

