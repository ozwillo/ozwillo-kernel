package oasis.web.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;

class IncomingNotification {

  @JsonProperty String[] user_ids;

  @JsonProperty String service_id;

  @JsonProperty String message;

  @JsonProperty String action_uri;

  @JsonProperty String action_label;
}

