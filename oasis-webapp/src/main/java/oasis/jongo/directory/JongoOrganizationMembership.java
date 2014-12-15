package oasis.jongo.directory;

import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.jongo.etag.HasModified;
import oasis.model.directory.OrganizationMembership;

public class JongoOrganizationMembership extends OrganizationMembership implements HasModified {
  @JsonProperty
  private Long created; // XXX: not exposed, only initialized once

  @JsonProperty
  private long modified = System.currentTimeMillis();

  public JongoOrganizationMembership() {
  }

  public JongoOrganizationMembership(OrganizationMembership organizationMember) {
    super(organizationMember);
  }

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
