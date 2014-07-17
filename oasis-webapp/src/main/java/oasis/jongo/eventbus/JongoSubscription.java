package oasis.jongo.eventbus;

import javax.annotation.Nonnull;

import oasis.jongo.etag.HasModified;
import oasis.model.eventbus.Subscription;

public class JongoSubscription extends Subscription implements HasModified {

  private String instance_id;

  private long modified = System.currentTimeMillis();

  public JongoSubscription() {
  }

  public JongoSubscription(@Nonnull Subscription other) {
    super(other);
  }

  public String getInstance_id() {
    return instance_id;
  }

  public void setInstance_id(String instance_id) {
    this.instance_id = instance_id;
  }

  @Override
  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
