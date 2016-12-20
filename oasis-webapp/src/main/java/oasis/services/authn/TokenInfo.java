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
package oasis.services.authn;

import static oasis.services.authn.TokenHandler.makeId;

import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.model.authn.Token;

public class TokenInfo {
  @JsonProperty
  private String id;
  @JsonProperty
  private Instant iat;
  @JsonProperty
  private Instant exp;

  public TokenInfo() {
  }

  public TokenInfo(Token token, String pass) {
    this.id = makeId(token.getId(), pass);
    this.iat = token.getCreationTime();
    this.exp = token.getExpirationTime();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Instant getIat() {
    return iat;
  }

  public void setIat(Instant iat) {
    this.iat = iat;
  }

  public Instant getExp() {
    return exp;
  }

  public void setExp(Instant exp) {
    this.exp = exp;
  }
}
