/**
 * Ozwillo Kernel
 * Copyright (C) 2016  The Ozwillo Kernel Authors
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

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClientCertificate {
  @JsonProperty private String subject_dn;
  @JsonProperty private String issuer_dn;
  @JsonProperty private ClientType client_type;
  @JsonProperty private String client_id;

  public String getSubject_dn() {
    return subject_dn;
  }

  public void setSubject_dn(String subject_dn) {
    this.subject_dn = subject_dn;
  }

  public String getIssuer_dn() {
    return issuer_dn;
  }

  public void setIssuer_dn(String issuer_dn) {
    this.issuer_dn = issuer_dn;
  }

  public ClientType getClient_type() {
    return client_type;
  }

  public void setClient_type(ClientType client_type) {
    this.client_type = client_type;
  }

  public String getClient_id() {
    return client_id;
  }

  public void setClient_id(String client_id) {
    this.client_id = client_id;
  }
}
