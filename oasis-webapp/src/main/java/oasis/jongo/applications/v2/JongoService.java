package oasis.jongo.applications.v2;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.jongo.etag.HasModified;
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.Service;

public class JongoService extends Service implements HasModified {

  @JsonProperty
  private Long created; // XXX: not exposed, only initialized once

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

  void initCreated() {
    created = System.currentTimeMillis();
  }
}
