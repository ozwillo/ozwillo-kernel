/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.model.directory;

import org.joda.time.Instant;

import oasis.model.annotations.Id;

public class OrganizationMembership {
  @Id
  private String id;
  private String accountId;
  private String email;
  private String organizationId;
  private boolean admin;
  private Status status;
  private Instant created;
  private Instant accepted;
  private String creator_id;

  public OrganizationMembership() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy the {@link #id} field.
   */
  public OrganizationMembership(OrganizationMembership other) {
    accountId = other.getAccountId();
    email = other.getEmail();
    organizationId = other.getOrganizationId();
    admin = other.isAdmin();
    status = other.getStatus();
    created = other.getCreated();
    accepted = other.getAccepted();
    creator_id = other.getCreator_id();
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

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
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

  public Status getStatus() {
    // Old memberships can have a null status
    // As the invitation were not created, they can be considered as accepted
    if (status == null) {
      return Status.ACCEPTED;
    }
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Instant getCreated() {
    return created;
  }

  public void setCreated(Instant created) {
    this.created = created;
  }

  public Instant getAccepted() {
    return accepted;
  }

  public void setAccepted(Instant accepted) {
    this.accepted = accepted;
  }

  public String getCreator_id() {
    return creator_id;
  }

  public void setCreator_id(String creator_id) {
    this.creator_id = creator_id;
  }

  public enum Status {
    PENDING, ACCEPTED
  }
}
