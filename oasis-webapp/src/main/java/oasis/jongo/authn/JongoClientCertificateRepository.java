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
package oasis.jongo.authn;

import com.mongodb.DuplicateKeyException;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import oasis.jongo.JongoBootstrapper;
import oasis.model.authn.ClientCertificate;
import oasis.model.authn.ClientCertificateRepository;
import oasis.model.authn.ClientType;

import javax.inject.Inject;

public class JongoClientCertificateRepository implements ClientCertificateRepository, JongoBootstrapper {
  private final Jongo jongo;

  @Inject
  JongoClientCertificateRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  protected MongoCollection getClientCertificateCollection() {
    return jongo.getCollection("client_certificate");
  }

  @Override
  public ClientCertificate saveClientCertificate(ClientCertificate clientCertificate) {
    try {
      getClientCertificateCollection().insert(clientCertificate);
    } catch (DuplicateKeyException e) {
      return null;
    }
    return clientCertificate;
  }

  @Override
  public ClientCertificate getClientCertificate(String subjectDN, String issuerDN) {
    return getClientCertificateCollection().findOne("{ subject_dn:#, issuer_dn:# }", subjectDN, issuerDN).as(ClientCertificate.class);
  }

  @Override
  public boolean deleteClientCertificate(ClientType clientType, String clientId, String certId) {
    return getClientCertificateCollection().remove("{ id:#, client_type:#, client_id:# }", certId, clientType, clientId)
        .getN() > 0;
  }

  @Override
  public Iterable<ClientCertificate> getClientCertificatesForClient(ClientType clientType, String clientId) {
    return getClientCertificateCollection().find("{ client_type:#, client_id:# }", clientType, clientId)
        .as(ClientCertificate.class);
  }

  @Override
  public void bootstrap() {
    getClientCertificateCollection().ensureIndex("{ subject_dn: 1, issuer_dn: 1 }", "{ unique: 1 }");
  }
}
