package oasis.jongo.social;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonRootName;

import oasis.jongo.etag.HasModified;
import oasis.model.social.Identity;

@JsonRootName("identity")
public class JongoIdentity extends Identity implements HasModified {

  private long modified = System.currentTimeMillis();

  JongoIdentity() {
    super();
  }

  JongoIdentity(@Nonnull Identity other) {
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
