package oasis.jongo.applications;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonRootName;

import oasis.jongo.etag.HasModified;
import oasis.model.applications.DataProvider;

@Deprecated
@JsonRootName("dataProvider")
public class JongoDataProvider extends DataProvider implements HasModified {

  private long modified = System.currentTimeMillis();

  public JongoDataProvider() {
  }

  public JongoDataProvider(@Nonnull DataProvider other) {
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
