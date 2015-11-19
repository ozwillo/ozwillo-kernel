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
package oasis.model.directory;

import java.net.URI;

import javax.annotation.Nonnull;

import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import oasis.model.annotations.Id;

@JsonRootName("organization")
public class Organization {

  @Id
  private String id;

  @JsonProperty
  private String name;

  @JsonProperty
  private Type type;
  private URI territory_id;
  private URI dc_id;

  private Status status;
  private Instant status_changed;
  private String status_change_requester_id;

  public Organization() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public Organization(@Nonnull Organization other) {
    this.name = other.getName();
    this.type = other.getType();
    this.territory_id = other.getTerritory_id();
    this.dc_id = other.getDc_id();
    this.status = other.getStatus();
    this.status_changed = other.getStatus_changed();
    this.status_change_requester_id = other.getStatus_change_requester_id();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public URI getTerritory_id() {
    return territory_id;
  }

  public void setTerritory_id(URI territory_id) {
    this.territory_id = territory_id;
  }

  public URI getDc_id() {
    return dc_id;
  }

  public Organization setDc_id(URI dc_id) {
    this.dc_id = dc_id;
    return this;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Instant getStatus_changed() {
    return status_changed;
  }

  public void setStatus_changed(Instant status_changed) {
    this.status_changed = status_changed;
  }

  public String getStatus_change_requester_id() {
    return status_change_requester_id;
  }

  public void setStatus_change_requester_id(String status_change_requester_id) {
    this.status_change_requester_id = status_change_requester_id;
  }

  public enum Type {
    PUBLIC_BODY,
    COMPANY
  }

  public enum Status {
    AVAILABLE, DELETED
  }
}
