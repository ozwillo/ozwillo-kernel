package oasis.jongo.applications.v2;

import javax.annotation.Nonnull;

import oasis.jongo.etag.HasModified;
import oasis.model.applications.v2.Application;

public class JongoApplication extends Application implements HasModified {

  private long modified = System.currentTimeMillis();

  public JongoApplication() {
    super();
  }

  public JongoApplication(@Nonnull Application other) {
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
