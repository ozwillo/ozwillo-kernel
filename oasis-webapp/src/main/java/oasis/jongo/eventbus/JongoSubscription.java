package oasis.jongo.eventbus;

import javax.annotation.Nonnull;

import oasis.jongo.etag.HasModified;
import oasis.model.eventbus.Subscription;

public class JongoSubscription extends Subscription implements HasModified {

  private String application_id;

  private long modified = System.currentTimeMillis();

  public JongoSubscription() {
  }

  public JongoSubscription(@Nonnull Subscription other) {
    super(other);
  }

  public String getApplication_id() {
    return application_id;
  }

  public void setApplication_id(String application_id) {
    this.application_id = application_id;
  }

  @Override
  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
