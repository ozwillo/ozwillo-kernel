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
package oasis.model.authz;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonRootName;

import oasis.model.annotations.Id;

@JsonRootName("authorizedScopes")
public class AuthorizedScopes {
  @Id
  private String id;
  private String account_id;
  private String client_id;
  private Set<String> scope_ids = new HashSet<>();
  private Set<String> claim_names = new HashSet<>();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getAccount_id() {
    return account_id;
  }

  public void setAccount_id(String account_id) {
    this.account_id = account_id;
  }

  public String getClient_id() {
    return client_id;
  }

  public void setClient_id(String client_id) {
    this.client_id = client_id;
  }

  public Set<String> getScope_ids() {
    return Collections.unmodifiableSet(scope_ids);
  }

  public void setScope_ids(Set<String> scope_ids) {
    this.scope_ids = new HashSet<>(scope_ids);
  }

  public Set<String> getClaim_names() {
    return claim_names;
  }

  public void setClaim_names(Set<String> claim_names) {
    this.claim_names = claim_names;
  }
}
