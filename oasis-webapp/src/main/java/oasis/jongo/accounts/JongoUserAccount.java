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
package oasis.jongo.accounts;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.jongo.etag.HasModified;
import oasis.model.accounts.UserAccount;

public class JongoUserAccount extends UserAccount implements HasModified {

  @JsonProperty
  private Long activated_at; // XXX: not exposed, only initialized once

  public JongoUserAccount() {
    setUpdated_at(System.currentTimeMillis());
  }

  public JongoUserAccount(@Nonnull UserAccount other) {
    super(other);
    setUpdated_at(System.currentTimeMillis());
  }

  @Override
  @JsonIgnore
  public long getModified() {
    return getUpdated_at();
  }

  public void initCreated_at() {
    setCreated_at(System.currentTimeMillis());
  }

  public void initActivated_at() {
    activated_at = System.currentTimeMillis();
  }
}
