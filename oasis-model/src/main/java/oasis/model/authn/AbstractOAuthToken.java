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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.model.bootstrap.ClientIds;

public abstract class AbstractOAuthToken extends AbstractAccountToken {
  @JsonProperty
  private String serviceProviderId;

  @JsonProperty
  private Set<String> scopeIds = new HashSet<>();

  @JsonProperty
  private Set<String> claimNames = new HashSet<>();

  @JsonProperty
  private Boolean portal;

  public String getServiceProviderId() {
    return serviceProviderId;
  }

  public void setServiceProviderId(String serviceProviderId) {
    this.serviceProviderId = serviceProviderId;
  }

  public Set<String> getScopeIds() {
    return Collections.unmodifiableSet(scopeIds);
  }

  public void setScopeIds(Set<String> scopeIds) {
    this.scopeIds = new HashSet<>(scopeIds);
  }

  public Set<String> getClaimNames() {
    return Collections.unmodifiableSet(claimNames);
  }

  public void setClaimNames(Set<String> claimNames) {
    this.claimNames = new HashSet<>(claimNames);
  }

  @JsonIgnore
  public boolean isPortal() {
    return Boolean.TRUE.equals(portal)
        // backwards compatibility
        || (portal == null && ClientIds.PORTAL.equals(serviceProviderId));
  }

  @JsonIgnore
  public void setPortal(boolean portal) {
    this.portal = portal ? Boolean.TRUE : null;
  }
}
