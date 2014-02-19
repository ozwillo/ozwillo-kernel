package oasis.jongo.social;

import java.util.List;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.google.common.collect.ImmutableList;

import oasis.jongo.etag.HasModified;
import oasis.model.social.Identity;

@JsonRootName("identity")
public class JongoIdentity extends Identity implements HasModified {

  private long modified = System.currentTimeMillis();

  @JsonProperty
  private ImmutableList<JongoRelation> relations = ImmutableList.of();

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

  public ImmutableList<JongoRelation> getRelations() {
    return relations;
  }

  public void setRelations(List<JongoRelation> relations) {
    this.relations = ImmutableList.copyOf(relations);
  }
}
