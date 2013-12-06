package oasis.jongo.applications;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonRootName;

import oasis.jongo.etag.HasModified;
import oasis.model.applications.Subscription;

@JsonRootName("subscription")
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
