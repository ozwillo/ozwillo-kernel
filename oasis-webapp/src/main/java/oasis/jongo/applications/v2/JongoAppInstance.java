package oasis.jongo.applications.v2;

import javax.annotation.Nonnull;

import oasis.jongo.etag.HasModified;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.Application;

public class JongoAppInstance extends AppInstance implements HasModified {

  private long modified = System.currentTimeMillis();

  public JongoAppInstance() {
    super();
  }

  public JongoAppInstance(@Nonnull AppInstance other) {
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
