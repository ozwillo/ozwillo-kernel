package oasis.jongo.social;

import java.util.List;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.google.common.collect.ImmutableList;

import oasis.jongo.etag.HasModified;
import oasis.model.social.Relation;

@JsonRootName("relation")
public class JongoRelation extends Relation implements HasModified {

  @JsonProperty
  private ImmutableList<String> identityIds = ImmutableList.of();

  private long modified = System.currentTimeMillis();

  JongoRelation() {
    super();
  }

  JongoRelation(@Nonnull JongoRelation other) {
    super(other);
  }

  public ImmutableList<String> getIdentityIds() {
    return identityIds;
  }

  public void setIdentityIds(List<String> identityIds) {
    this.identityIds = ImmutableList.copyOf(identityIds);
  }

  @Override
  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
