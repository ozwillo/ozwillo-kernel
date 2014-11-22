package oasis.jongo.eventbus;

import javax.annotation.Nonnull;

import oasis.jongo.etag.HasModified;
import oasis.model.eventbus.Subscription;

public class JongoSubscription extends Subscription implements HasModified {

  private long modified = System.currentTimeMillis();

  public JongoSubscription() {
  }

  public JongoSubscription(@Nonnull Subscription other) {
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
