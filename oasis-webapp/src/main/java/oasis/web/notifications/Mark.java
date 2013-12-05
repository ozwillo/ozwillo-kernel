package oasis.web.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.notification.Notification;

/*
 * Extracted from {@link NotificationEndpoint} for swagger.
 */
@ApiModel
class Mark {

  @JsonProperty
  @ApiModelProperty
  String[] messageIds;

  @JsonProperty
  @ApiModelProperty(dataType = "String", allowableValues = "READ,UNREAD")
  Notification.Status status;

  // For swagger
  public String[] getMessageIds() {
    return messageIds;
  }

  // For swagger
  public Notification.Status getStatus() {
    return status;
  }
}
