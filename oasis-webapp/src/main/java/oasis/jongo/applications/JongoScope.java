package oasis.jongo.applications;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonRootName;

import oasis.jongo.etag.HasModified;
import oasis.model.applications.Scope;

@JsonRootName("scope")
public class JongoScope extends Scope implements HasModified {

  private long modified = System.currentTimeMillis();

  public JongoScope() {
  }

  public JongoScope(@Nonnull Scope other) {
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
