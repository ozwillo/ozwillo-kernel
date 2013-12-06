package oasis.jongo.notification;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonRootName;

import oasis.jongo.etag.HasModified;
import oasis.model.notification.Notification;

@JsonRootName("notification")
class JongoNotification extends Notification implements HasModified {

  private long modified = System.currentTimeMillis();

  JongoNotification() {
    super();
  }

  JongoNotification(@Nonnull Notification other) {
    super(other);
  }

  @Override
  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
