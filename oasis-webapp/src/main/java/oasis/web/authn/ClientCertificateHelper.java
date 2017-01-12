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
package oasis.web.authn;

import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.core.MultivaluedMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import oasis.auth.AuthModule;
import oasis.model.authn.ClientCertificate;
import oasis.model.authn.ClientCertificateRepository;
import org.immutables.value.Value;

@Value.Enclosing
public class ClientCertificateHelper {
  @VisibleForTesting static final String CLIENT_CERTIFICATE_SUBJECT_DN_HEADER_NAME = "X-SSL-Client-Subject-DN";
  @VisibleForTesting static final String CLIENT_CERTIFICATE_ISSUER_DN_HEADER_NAME = "X-SSL-Client-Issuer-DN";

  private final AuthModule.Settings settings;
  private final ClientCertificateRepository clientCertificateRepository;

  @Inject
  public ClientCertificateHelper(AuthModule.Settings settings, ClientCertificateRepository clientCertificateRepository) {
    this.settings = settings;
    this.clientCertificateRepository = clientCertificateRepository;
  }

  @Nullable
  public ClientCertificateData getClientCertificateData(MultivaluedMap<String, String> headers) {
    if (!settings.enableClientCertificates) {
      return null;
    }
    final String subjectDN = getSingleHeader(headers, CLIENT_CERTIFICATE_SUBJECT_DN_HEADER_NAME);
    final String issuerDN = getSingleHeader(headers, CLIENT_CERTIFICATE_ISSUER_DN_HEADER_NAME);
    if (Strings.isNullOrEmpty(subjectDN) || Strings.isNullOrEmpty(issuerDN)) {
      return null;
    }
    return ImmutableClientCertificateHelper.ClientCertificateData.of(subjectDN, issuerDN);
  }

  @Nullable
  public ClientCertificate getClientCertificate(MultivaluedMap<String, String> headers) {
    ClientCertificateData clientCertificateData = getClientCertificateData(headers);
    if (clientCertificateData == null) {
      return null;
    }
    return clientCertificateRepository.getClientCertificate(clientCertificateData.getSubjectDN(), clientCertificateData.getIssuerDN());
  }

  @Nullable
  private String getSingleHeader(MultivaluedMap<String, String> headers, String headerName) {
    final List<String> header = headers.get(headerName);
    if (header == null || header.size() != 1 || header.get(0) == null) {
      return null;
    }
    return header.get(0).trim();
  }

  @Value.Immutable
  public interface ClientCertificateData {
    @Value.Parameter(order = 1) String getSubjectDN();
    @Value.Parameter(order = 2) String getIssuerDN();
  }
}
