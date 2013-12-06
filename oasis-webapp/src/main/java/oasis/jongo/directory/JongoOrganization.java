package oasis.jongo.directory;

import java.util.List;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import oasis.jongo.etag.HasModified;
import oasis.model.directory.Organization;

@JsonRootName("organization")
class JongoOrganization extends Organization implements HasModified {

  @JsonProperty
  private List<JongoGroup> groups;

  private long modified = System.currentTimeMillis();

  JongoOrganization() {
    super();
  }

  JongoOrganization(@Nonnull Organization other) {
    super(other);
  }

  public List<JongoGroup> getGroups() {
    return groups;
  }

  public void setGroups(List<JongoGroup> groups) {
    this.groups = groups;
  }

  @Override
  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
