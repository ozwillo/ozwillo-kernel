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

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assume.assumeFalse;
import static org.mockito.Mockito.*;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import org.jboss.resteasy.specimpl.UnmodifiableMultivaluedMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import oasis.auth.AuthModule;
import oasis.model.authn.ClientCertificate;
import oasis.model.authn.ClientCertificateRepository;
import oasis.model.authn.ClientType;

@RunWith(MockitoJUnitRunner.class)
public class ClientCertificateHelperTest {

  static final AuthModule.Settings enabledSettings = AuthModule.Settings.builder()
      .setEnableClientCertificates(true)
      .build();

  static final ClientCertificate validCertificate = new ClientCertificate() {{
    setId("valid certificate");
    setSubject_dn("valid subject");
    setIssuer_dn("valid issuer");
    setClient_type(ClientType.USER);
    setClient_id("user account");
  }};

  static final MultivaluedMap<String, String> headers;
  static {
    MultivaluedHashMap<String, String> h = new MultivaluedHashMap<>();
    h.putSingle(ClientCertificateHelper.CLIENT_CERTIFICATE_SUBJECT_DN_HEADER_NAME, validCertificate.getSubject_dn());
    h.putSingle(ClientCertificateHelper.CLIENT_CERTIFICATE_ISSUER_DN_HEADER_NAME, validCertificate.getIssuer_dn());
    headers = new UnmodifiableMultivaluedMap<>(h);
  }

  @Mock ClientCertificateRepository clientCertificateRepository;

  @Before public void setupMocks() {
    when(clientCertificateRepository.getClientCertificate(validCertificate.getSubject_dn(), validCertificate.getIssuer_dn())).thenReturn(validCertificate);
  }

  @After public void verifyMocks() {
    verify(clientCertificateRepository, never()).saveClientCertificate(any(ClientCertificate.class));
    verify(clientCertificateRepository, never()).deleteClientCertificate(any(ClientType.class), anyString(), anyString());
    verify(clientCertificateRepository, never()).getClientCertificatesForClient(any(ClientType.class), anyString());
  }

  @Test public void testDisabledClientCertificates() {
    ClientCertificateHelper clientCertificateHelper = new ClientCertificateHelper(
        AuthModule.Settings.builder()
            .setEnableClientCertificates(false)
            .build(),
        clientCertificateRepository);

    ClientCertificate clientCertificate = clientCertificateHelper.getClientCertificate(headers);
    assertThat(clientCertificate).isNull();

    verify(clientCertificateRepository, never()).getClientCertificate(anyString(), anyString());
  }

  @Test public void testNoCertificate() {
    ClientCertificateHelper clientCertificateHelper = new ClientCertificateHelper(enabledSettings, clientCertificateRepository);

    ClientCertificate clientCertificate = clientCertificateHelper.getClientCertificate(new MultivaluedHashMap<>());
    assertThat(clientCertificate).isNull();

    verify(clientCertificateRepository, never()).getClientCertificate(anyString(), anyString());
  }

  @Test public void testKnownCertificate() {
    ClientCertificateHelper clientCertificateHelper = new ClientCertificateHelper(enabledSettings, clientCertificateRepository);

    ClientCertificate clientCertificate = clientCertificateHelper.getClientCertificate(headers);
    assertThat(clientCertificate).isEqualToComparingFieldByField(validCertificate);
  }

  @Test public void testUnknownCertificate() {
    ClientCertificateHelper clientCertificateHelper = new ClientCertificateHelper(enabledSettings, clientCertificateRepository);

    MultivaluedMap<String, String> h = new MultivaluedHashMap<>(headers);
    h.putSingle(ClientCertificateHelper.CLIENT_CERTIFICATE_SUBJECT_DN_HEADER_NAME, "unknown subject");
    assumeFalse(headers.equalsIgnoreValueOrder(h));

    ClientCertificate clientCertificate = clientCertificateHelper.getClientCertificate(h);
    assertThat(clientCertificate).isNull();

    verify(clientCertificateRepository).getClientCertificate("unknown subject", validCertificate.getIssuer_dn());
  }

  @Test public void testMissingSubject() {
    ClientCertificateHelper clientCertificateHelper = new ClientCertificateHelper(enabledSettings, clientCertificateRepository);

    MultivaluedMap<String, String> h = new MultivaluedHashMap<>(headers);
    h.remove(ClientCertificateHelper.CLIENT_CERTIFICATE_SUBJECT_DN_HEADER_NAME);

    ClientCertificate clientCertificate = clientCertificateHelper.getClientCertificate(h);
    assertThat(clientCertificate).isNull();

    verify(clientCertificateRepository, never()).getClientCertificate(anyString(), anyString());
  }

  @Test public void testMissingIssuer() {
    ClientCertificateHelper clientCertificateHelper = new ClientCertificateHelper(enabledSettings, clientCertificateRepository);

    MultivaluedMap<String, String> h = new MultivaluedHashMap<>(headers);
    h.remove(ClientCertificateHelper.CLIENT_CERTIFICATE_ISSUER_DN_HEADER_NAME);

    ClientCertificate clientCertificate = clientCertificateHelper.getClientCertificate(h);
    assertThat(clientCertificate).isNull();

    verify(clientCertificateRepository, never()).getClientCertificate(anyString(), anyString());
  }

  @Test public void testTooManySubjects() {
    ClientCertificateHelper clientCertificateHelper = new ClientCertificateHelper(enabledSettings, clientCertificateRepository);

    MultivaluedMap<String, String> h = new MultivaluedHashMap<>(headers);
    h.add(ClientCertificateHelper.CLIENT_CERTIFICATE_SUBJECT_DN_HEADER_NAME, "other subject");

    ClientCertificate clientCertificate = clientCertificateHelper.getClientCertificate(h);
    assertThat(clientCertificate).isNull();

    verify(clientCertificateRepository, never()).getClientCertificate(anyString(), anyString());
  }

  @Test public void testTooManyIssuers() {
    ClientCertificateHelper clientCertificateHelper = new ClientCertificateHelper(enabledSettings, clientCertificateRepository);

    MultivaluedMap<String, String> h = new MultivaluedHashMap<>(headers);
    h.add(ClientCertificateHelper.CLIENT_CERTIFICATE_ISSUER_DN_HEADER_NAME, "other issuer");

    ClientCertificate clientCertificate = clientCertificateHelper.getClientCertificate(h);
    assertThat(clientCertificate).isNull();

    verify(clientCertificateRepository, never()).getClientCertificate(anyString(), anyString());
  }
}