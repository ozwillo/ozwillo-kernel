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
package oasis.model.authn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joda.time.Duration;
import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import oasis.model.annotations.Id;

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "_type")
public abstract class Token {
  @Id
  private String id;
  @JsonProperty
  private byte[] hash;
  @JsonProperty
  private byte[] salt;
  @JsonProperty
  private Instant creationTime = Instant.now();
  @JsonProperty
  private Instant expirationTime;
  @JsonProperty
  private List<String> ancestorIds = new ArrayList<>();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public byte[] getHash() {
    return hash;
  }

  public void setHash(byte[] hash) {
    this.hash = hash;
  }

  public byte[] getSalt() {
    return salt;
  }

  public void setSalt(byte[] salt) {
    this.salt = salt;
  }

  public Instant getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(Instant creationTime) {
    this.creationTime = creationTime;
  }

  public Instant getExpirationTime() {
    return expirationTime;
  }

  public void setExpirationTime(Instant expirationTime) {
    this.expirationTime = expirationTime;
  }

  public List<String> getAncestorIds() {
    return Collections.unmodifiableList(ancestorIds);
  }

  public void setAncestorIds(List<String> ancestorIds) {
    this.ancestorIds = new ArrayList<>(ancestorIds);
  }

  public void setParent(Token parent) {
    this.ancestorIds = new ArrayList<>();
    this.ancestorIds.addAll(parent.getAncestorIds());
    this.ancestorIds.add(parent.getId());
  }

  public Duration expiresIn() {
    return new Duration(creationTime, expirationTime);
  }

  public void expiresIn(Duration duration) {
      if (this.creationTime == null) {
        throw new IllegalStateException("Cannot compute expiration time from duration; creation time has not been set.");
      }
      setExpirationTime(getCreationTime().plus(duration));
  }

  public void checkValidity() {
    // valid by default
  }
}
