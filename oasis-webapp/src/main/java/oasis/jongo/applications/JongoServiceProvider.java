package oasis.jongo.applications;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonRootName;

import oasis.jongo.etag.HasModified;
import oasis.model.applications.ServiceProvider;

@Deprecated
@JsonRootName("serviceProvider")
public class JongoServiceProvider extends ServiceProvider implements HasModified {

  private long modified = System.currentTimeMillis();

  JongoServiceProvider() {
    super();
  }

  JongoServiceProvider(@Nonnull ServiceProvider other) {
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
