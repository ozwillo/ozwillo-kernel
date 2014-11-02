package oasis.web.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.model.i18n.LocalizableString;

class IncomingNotification {

  @JsonProperty String[] user_ids;

  @JsonProperty String service_id;

  @JsonProperty LocalizableString message = new LocalizableString();

  @JsonProperty LocalizableString action_uri = new LocalizableString();

  @JsonProperty LocalizableString action_label = new LocalizableString();
}

