package oasis.services.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.notification.Notification;

@JsonRootName("notification")
class JongoNotification extends Notification {

  @JsonProperty
  @ApiModelProperty
  private long modified = System.currentTimeMillis();

  JongoNotification() {
    super();
  }

  JongoNotification(Notification other) {
    super(other);
  }

  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
