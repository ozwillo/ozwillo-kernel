package oasis.jongo.applications.v2;

import oasis.jongo.etag.HasModified;
import oasis.model.applications.v2.CatalogEntry;

public class JongoCatalogEntry extends CatalogEntry implements HasModified {
  private EntryType type;
  private long modified = System.currentTimeMillis();

  public JongoCatalogEntry() {
    super();
  }

  public JongoCatalogEntry(CatalogEntry other) {
    super(other);
  }

  @Override
  public EntryType getType() {
    return type;
  }

  public void setType(EntryType type) {
    this.type = type;
  }

  @Override
  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
