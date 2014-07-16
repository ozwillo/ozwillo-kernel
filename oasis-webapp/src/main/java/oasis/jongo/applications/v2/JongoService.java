package oasis.jongo.applications.v2;

import javax.annotation.Nonnull;

import oasis.jongo.etag.HasModified;
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.Service;

public class JongoService extends Service implements HasModified {

  private long modified = System.currentTimeMillis();

  public JongoService() {
    super();
  }

  public JongoService(@Nonnull Service other) {
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
