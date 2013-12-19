package oasis.jongo.social;

import java.util.List;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import oasis.jongo.etag.HasModified;
import oasis.model.directory.Group;
import oasis.model.social.Relation;

@JsonRootName("relation")
public class JongoRelation extends Relation implements HasModified {

  @JsonProperty
  private List<String> identityIds;

  private long modified = System.currentTimeMillis();

  JongoRelation() {
    super();
  }

  JongoRelation(@Nonnull JongoRelation other) {
    super(other);
  }

  public List<String> getIdentityIds() {
    return identityIds;
  }

  public void setIdentityIds(List<String> identityIds) {
    this.identityIds = identityIds;
  }

  @Override
  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
