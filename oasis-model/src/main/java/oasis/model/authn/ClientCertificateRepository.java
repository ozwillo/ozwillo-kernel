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

public interface ClientCertificateRepository {
  ClientCertificate saveClientCertificate(ClientCertificate clientCertificate);

  ClientCertificate getClientCertificate(String subjectDN, String issuerDN);

  /** Deletes by ID, but only if the clientType and clientId match. */
  boolean deleteClientCertificate(ClientType clientType, String clientId, String certId);

  Iterable<ClientCertificate> getClientCertificatesForClient(ClientType clientType, String clientId);
}