package oasis.model.directory;

import oasis.model.annotations.Id;

public class OrganizationMembership {
  @Id
  private String id;
  private String accountId;
  private String organizationId;
  private boolean admin;

  public OrganizationMembership() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy the {@link #id} field.
   */
  public OrganizationMembership(OrganizationMembership other) {
    accountId = other.getAccountId();
    organizationId = other.getOrganizationId();
    admin = other.isAdmin();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getAccountId() {
    return accountId;
  }

  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public boolean isAdmin() {
    return admin;
  }

  public void setAdmin(boolean admin) {
    this.admin = admin;
  }
}
