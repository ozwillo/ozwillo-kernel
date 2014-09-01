package oasis.jongo.applications.v2;

import javax.annotation.Nonnull;

import oasis.jongo.etag.HasModified;
import oasis.model.applications.v2.AccessControlEntry;

public class JongoAccessControlEntry extends AccessControlEntry implements HasModified {
  private long modified = System.currentTimeMillis();

  public JongoAccessControlEntry() {
    super();
  }

  public JongoAccessControlEntry(@Nonnull AccessControlEntry other) {
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
