/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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
package oasis.model.applications.v2;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joda.time.Instant;

import oasis.model.annotations.Id;

public class AccessControlEntry {
  @Id
  private String id;
  private String instance_id;
  @Nullable private String user_id;
  @Nullable private String email;
  private Status status;
  private Instant created;
  private Instant accepted;
  private String creator_id;

  public AccessControlEntry() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy the {@link #id} field.
   */
  public AccessControlEntry(@Nonnull AccessControlEntry other) {
    instance_id = other.getInstance_id();
    user_id = other.getUser_id();
    creator_id = other.getCreator_id();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getInstance_id() {
    return instance_id;
  }

  public void setInstance_id(String instance_id) {
    this.instance_id = instance_id;
  }

  @Nullable
  public String getUser_id() {
    return user_id;
  }

  public void setUser_id(@Nullable String user_id) {
    this.user_id = user_id;
  }

  @Nullable
  public String getEmail() {
    return email;
  }

  public void setEmail(@Nullable String email) {
    this.email = email;
  }

  public Status getStatus() {
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
