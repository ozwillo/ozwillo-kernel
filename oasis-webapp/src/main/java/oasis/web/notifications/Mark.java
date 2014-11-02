package oasis.web.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.notification.Notification;

/*
 * Extracted from {@link NotificationEndpoint} for swagger.
 */
class Mark {

  @JsonProperty String[] message_ids;

  @JsonProperty Notification.Status status;
}
