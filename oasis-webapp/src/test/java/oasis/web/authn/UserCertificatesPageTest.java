/**
 * Ozwillo Kernel
 * Copyright (C) 2017  The Ozwillo Kernel Authors
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
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import org.joda.time.Duration;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import oasis.auditlog.noop.NoopAuditLogModule;
import oasis.http.testing.InProcessResteasy;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ClientCertificate;
import oasis.model.authn.ClientCertificateRepository;
import oasis.model.authn.ClientType;
import oasis.model.authn.SidToken;
import oasis.soy.TestingSoyGuiceModule;
import oasis.urls.ImmutableUrls;
import oasis.urls.UrlsModule;
import oasis.web.authn.ClientCertificateHelper.ClientCertificateData;
import oasis.web.authn.testing.TestUserFilter;
import oasis.web.view.SoyTemplateBodyWriter;

@RunWith(JukitoRunner.class)
public class UserCertificatesPageTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(UserCertificatesPage.class);

      install(new NoopAuditLogModule());
      install(new TestingSoyGuiceModule());
      install(new UrlsModule(ImmutableUrls.builder().build()));

      bindMock(AccountRepository.class).in(TestSingleton.class);
      bindMock(ClientCertificateHelper.class).in(TestSingleton.class);
      bindMock(ClientCertificateRepository.class).in(TestSingleton.class);
    }
  }

  private static final UserAccount someUserAccount = new UserAccount() {{
    setId("someUser");
    setEmail_address("some@example.com");
  }};
  private static final SidToken someSidToken = new SidToken() {{
    setId("someSidToken");
    setAccountId(someUserAccount.getId());
    expiresIn(Duration.standardHours(1));
  }};
  private static final ClientCertificate someCertificate = new ClientCertificate() {{
    setId("some certificate");
    setSubject_dn("valid subject");
    setIssuer_dn("valid issuer");
    setClient_type(ClientType.USER);
    setClient_id(someUserAccount.getId());
  }};
  private static final ClientCertificateData someCertificateData = ImmutableClientCertificateHelper.ClientCertificateData
      .of(someCertificate.getSubject_dn(), someCertificate.getIssuer_dn());

  @Inject @Rule public InProcessResteasy resteasy;

  @Inject ClientCertificateRepository clientCertificateRepository;
  @Inject ClientCertificateHelper clientCertificateHelper;

  @Before public void setupMocks(AccountRepository accountRepository, ClientCertificateRepository clientCertificateRepository) {
    when(accountRepository.getUserAccountById(someUserAccount.getId())).thenReturn(someUserAccount);
//    when(clientCertificateRepository.getClientCertificate(someCertificate.getSubject_dn(), someCertificate.getIssuer_dn())).thenReturn(someCertificate);

    // We don't verify the HTML response, so just supply an empty list to avoid exceptions.
    when(clientCertificateRepository.getClientCertificatesForClient(ClientType.USER, someUserAccount.getId())).thenReturn(ImmutableList.<ClientCertificate>of());
  }

  @Before public void setUp() throws Exception {
    resteasy.getDeployment().getRegistry().addPerRequestResource(UserCertificatesPage.class);
    resteasy.getDeployment().getProviderFactory().register(SoyTemplateBodyWriter.class);
  }

  @SuppressWarnings("unchecked")
  @Test public void testAddCurrent() {
    when(clientCertificateHelper.getClientCertificateData(any(MultivaluedMap.class))).thenReturn(someCertificateData);
    when(clientCertificateRepository.saveClientCertificate(any(ClientCertificate.class))).thenReturn(someCertificate);

    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(someSidToken));

    Response response = resteasy.getClient().target(
        resteasy.getBaseUriBuilder()
            .path(UserCertificatesPage.class)
            .path(UserCertificatesPage.class, "addCurrent")
    ).request().post(Entity.form(new Form()
        .param("subject", someCertificateData.getSubjectDN())
        .param("issuer", someCertificateData.getIssuerDN())));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(resteasy.getBaseUriBuilder()
        .path(UserCertificatesPage.class)
        .path(UserCertificatesPage.class, "get")
        .build());

    ArgumentCaptor<ClientCertificate> clientCert = ArgumentCaptor.forClass(ClientCertificate.class);
    verify(clientCertificateRepository).saveClientCertificate(clientCert.capture());
    assertThat(clientCert.getValue().getId()).isNull();
    assertThat(clientCert.getValue()).isEqualToComparingOnlyGivenFields(someCertificate,
        "client_type", "client_id", "subject_dn", "issuer_dn");
  }

  @SuppressWarnings("unchecked")
  @Test public void testAddCurrent_alreadyUsingCert() {
    when(clientCertificateHelper.getClientCertificateData(any(MultivaluedMap.class))).thenReturn(someCertificateData);

    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(new SidToken() {{
      setId("someSidTokenUsingClientCertificate");
      setAccountId(someUserAccount.getId());
      expiresIn(Duration.standardHours(1));
      setUsingClientCertificate(true);
    }}));

    Response response = resteasy.getClient().target(
        resteasy.getBaseUriBuilder()
            .path(UserCertificatesPage.class)
            .path(UserCertificatesPage.class, "addCurrent")
    ).request().post(Entity.form(new Form()
        .param("subject", someCertificateData.getSubjectDN())
        .param("issuer", someCertificateData.getIssuerDN())));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(resteasy.getBaseUriBuilder()
        .path(UserCertificatesPage.class)
        .path(UserCertificatesPage.class, "get")
        .build());

    verify(clientCertificateRepository, never()).saveClientCertificate(any(ClientCertificate.class));
  }

  @Test public void testAddCurrent_noCert() {
    when(clientCertificateRepository.getClientCertificatesForClient(ClientType.USER, someUserAccount.getId())).thenReturn(ImmutableList.<ClientCertificate>of());

    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(someSidToken));

    Response response = resteasy.getClient().target(
        resteasy.getBaseUriBuilder()
            .path(UserCertificatesPage.class)
            .path(UserCertificatesPage.class, "addCurrent")
    ).request().post(Entity.form(new Form()
        .param("subject", someCertificateData.getSubjectDN())
        .param("issuer", someCertificateData.getIssuerDN())));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);

    verify(clientCertificateRepository, never()).saveClientCertificate(any(ClientCertificate.class));
  }

  @SuppressWarnings("unchecked")
  @Test public void testAddCurrent_mismatchingCertData() {
    when(clientCertificateHelper.getClientCertificateData(any(MultivaluedMap.class))).thenReturn(someCertificateData);

    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(someSidToken));

    Response response = resteasy.getClient().target(
        resteasy.getBaseUriBuilder()
            .path(UserCertificatesPage.class)
            .path(UserCertificatesPage.class, "addCurrent")
    ).request().post(Entity.form(new Form()
        .param("subject", "other subject")
        .param("issuer", someCertificateData.getIssuerDN())));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);

    verify(clientCertificateRepository, never()).saveClientCertificate(any(ClientCertificate.class));
  }

  @SuppressWarnings("unchecked")
  @Test public void testAddCurrent_certLinkedToOtherAccount() {
    ClientCertificateData otherCertificateData = ImmutableClientCertificateHelper.ClientCertificateData.of("other subject", someCertificateData.getIssuerDN());
    when(clientCertificateHelper.getClientCertificateData(any(MultivaluedMap.class))).thenReturn(otherCertificateData);
    when(clientCertificateRepository.saveClientCertificate(any(ClientCertificate.class))).thenReturn(null);

    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(someSidToken));

    Response response = resteasy.getClient().target(
        resteasy.getBaseUriBuilder()
            .path(UserCertificatesPage.class)
            .path(UserCertificatesPage.class, "addCurrent")
    ).request().post(Entity.form(new Form()
        .param("subject", otherCertificateData.getSubjectDN())
        .param("issuer", otherCertificateData.getIssuerDN())));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
  }

  @Test public void testRemove() {
    when(clientCertificateRepository.deleteClientCertificate(any(ClientType.class), anyString(), anyString())).thenReturn(true);

    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(someSidToken));

    Response response = resteasy.getClient().target(
        resteasy.getBaseUriBuilder()
            .path(UserCertificatesPage.class)
            .path(UserCertificatesPage.class, "remove")
    ).request().post(Entity.form(new Form("id", someCertificate.getId())));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(resteasy.getBaseUriBuilder()
        .path(UserCertificatesPage.class)
        .path(UserCertificatesPage.class, "get")
        .build());

    verify(clientCertificateRepository).deleteClientCertificate(ClientType.USER, someUserAccount.getId(), someCertificate.getId());
  }

  @Test public void testRemoveError() {
    when(clientCertificateRepository.deleteClientCertificate(any(ClientType.class), anyString(), anyString())).thenReturn(false);

    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(someSidToken));

    Response response = resteasy.getClient().target(
        resteasy.getBaseUriBuilder()
            .path(UserCertificatesPage.class)
            .path(UserCertificatesPage.class, "remove")
    ).request().post(Entity.form(new Form("id", someCertificate.getId())));

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);

    verify(clientCertificateRepository).deleteClientCertificate(ClientType.USER, someUserAccount.getId(), someCertificate.getId());
  }
}