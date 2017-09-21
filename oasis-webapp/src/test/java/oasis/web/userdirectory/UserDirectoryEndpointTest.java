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
package oasis.web.userdirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.model.authn.AccessToken;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.services.etag.EtagService;
import oasis.web.authn.testing.TestOAuthFilter;

@RunWith(JukitoRunner.class)
public class UserDirectoryEndpointTest {
  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(UserDirectoryEndpoint.class);
    }
  }

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(UserDirectoryEndpoint.class);
  }

  @Test public void testCreateOrganization(DirectoryRepository directoryRepository, EtagService etagService) throws Exception {
    when(directoryRepository.createOrganization(any(Organization.class))).thenAnswer(invocation -> {
      Organization organization = (Organization) invocation.getArguments()[0];
      organization = new Organization(organization);
      organization.setId("organization");
      organization.setStatus(Organization.Status.AVAILABLE);
      return organization;
    });
    when(etagService.getEtag(any())).thenReturn(new EntityTag("etag"));
    resteasy.getDeployment().getProviderFactory().register(new TestOAuthFilter(new AccessToken() {{
      setAccountId("user");
    }}));

    Organization sentOrganization = new Organization();
    sentOrganization.setName("Test");
    sentOrganization.setType(Organization.Type.PUBLIC_BODY);
    sentOrganization.setStatus(Organization.Status.AVAILABLE);

    Response response = resteasy.getClient()
        .target(resteasy.getBaseUriBuilder().path(UserDirectoryEndpoint.class).path(UserDirectoryEndpoint.class, "createOrganization")).request()
        .post(Entity.json(sentOrganization));

    // Check that the server correctly received the organization
    ArgumentCaptor<Organization> organization = ArgumentCaptor.forClass(Organization.class);
    verify(directoryRepository).createOrganization(organization.capture());
    assertThat(organization.getValue()).isEqualToComparingFieldByField(sentOrganization);

    // Check that "saving" the organization and serializing it back went well
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.CREATED);
    Organization createdOrganization = response.readEntity(Organization.class);
    assertThat(createdOrganization).isEqualToIgnoringGivenFields(sentOrganization, "id");
    assertThat(createdOrganization.getId()).isEqualTo("organization");
  }
}
