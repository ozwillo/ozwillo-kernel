package oasis.jongo.directory;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.jongo.etag.HasModified;
import oasis.model.directory.Organization;

@JsonRootName("organization")
class JongoOrganization extends Organization implements HasModified {

  @JsonProperty
  @ApiModelProperty
  private List<JongoGroup> groups;

  @JsonProperty
  @ApiModelProperty
  private long modified = System.currentTimeMillis();

  JongoOrganization() {
    super();
  }

  JongoOrganization(Organization other) {
    super(other);
  }

  public List<JongoGroup> getGroups() {
    return groups;
  }

  public void setGroups(List<JongoGroup> groups) {
    this.groups = groups;
  }

  public long getModified() {
    return modified;
  }

  public void setModified(long modified) {
    this.modified = modified;
  }
}
