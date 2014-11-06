package oasis.web.userdirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

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
    when(directoryRepository.createOrganization(any(Organization.class))).thenAnswer(new Answer<Organization>() {
      @Override
      public Organization answer(InvocationOnMock invocation) throws Throwable {
        Organization organization = (Organization) invocation.getArguments()[0];
        organization = new Organization(organization);
        organization.setId("organization");
        return organization;
      }
    });
    when(etagService.getEtag(any())).thenReturn("etag");
    resteasy.getDeployment().getProviderFactory().register(new TestOAuthFilter(new AccessToken() {{
      setAccountId("user");
    }}));

    Organization sentOrganization = new Organization();
    sentOrganization.setName("Test");
    sentOrganization.setType(Organization.Type.PUBLIC_BODY);

    Response response = resteasy.getClient()
        .target(UriBuilder.fromResource(UserDirectoryEndpoint.class).path(UserDirectoryEndpoint.class, "createOrganization")).request()
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
