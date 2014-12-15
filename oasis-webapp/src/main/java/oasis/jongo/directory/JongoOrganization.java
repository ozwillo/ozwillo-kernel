package oasis.jongo.directory;

import java.util.List;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.google.common.collect.ImmutableList;

import oasis.jongo.etag.HasModified;
import oasis.model.directory.Organization;

@JsonRootName("organization")
class JongoOrganization extends Organization implements HasModified {

  @JsonProperty
  private Long created; // XXX: not exposed, only initialized once

  @JsonProperty
  private ImmutableList<JongoGroup> groups = ImmutableList.of();

  private long modified = System.currentTimeMillis();

  JongoOrganization() {
    super();
  }

  JongoOrganization(@Nonnull Organization other) {
    super(other);
  }

  public ImmutableList<JongoGroup> getGroups() {
    return groups;
  }

  public void setGroups(List<JongoGroup> groups) {
    this.groups = ImmutableList.copyOf(groups);
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
