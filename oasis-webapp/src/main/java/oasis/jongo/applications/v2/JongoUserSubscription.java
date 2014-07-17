package oasis.jongo.applications.v2;

import javax.annotation.Nonnull;

import oasis.jongo.etag.HasModified;
import oasis.model.applications.v2.UserSubscription;

public class JongoUserSubscription extends UserSubscription implements HasModified {
  private long modified = System.currentTimeMillis();

  public JongoUserSubscription() {
    super();
  }

  public JongoUserSubscription(@Nonnull UserSubscription other) {
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
