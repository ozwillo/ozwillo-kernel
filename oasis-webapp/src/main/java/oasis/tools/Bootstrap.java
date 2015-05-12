/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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
package oasis.tools;

import javax.inject.Inject;
import javax.inject.Provider;

import org.jongo.Jongo;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;
import com.typesafe.config.Config;

import oasis.auth.AuthModule;
import oasis.jongo.JongoService;
import oasis.jongo.applications.v2.JongoAppInstance;
import oasis.jongo.applications.v2.JongoAppInstanceRepository;
import oasis.jongo.guice.JongoModule;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;
import oasis.model.applications.v2.Scope;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.ClientType;
import oasis.model.authz.Scopes;
import oasis.model.bootstrap.ClientIds;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.authn.CredentialsService;
import oasis.services.authn.PasswordGenerator;

public class Bootstrap extends CommandLineTool {

  public static void main(String[] args) throws Exception {
    new Bootstrap().run(args);
  }

  @Option(name = "-n", aliases = "--dry-run")
  private boolean dryRun;

  @Option(name = "-a", aliases = "--admin", required = true,
      usage = "Administrator's email address")
  private String adminEmail;

  @Option(name = "-p", aliases = "--password",
      usage = "Administrator's password")
  private String adminPassword;

  @Option(name = "-r", aliases = "--redirect-uri", required = true,
      usage = "Portal's redirect_uri")
  private String portalRedirectUri;

  @Option(name = "-plr", aliases = "--post-logout-redirect-uri", required = true,
      usage = "Portal's post_ogout_redirect_uri")
  private String portalPostLogoutRedirectUri;

  @Inject JongoService jongoService;
  @Inject Provider<Jongo> jongoProvider;
  @Inject Provider<ScopeRepository> scopeRepositoryProvider;
  @Inject Provider<AccountRepository> accountRepositoryProvider;
  @Inject Provider<CredentialsService> credentialsServiceProvider;
  @Inject Provider<PasswordGenerator> passwordGeneratorProvider;
  @Inject Provider<DirectoryRepository> directoryRepositoryProvider;
  @Inject Provider<OrganizationMembershipRepository> organizationMembershipRepositoryProvider;
  @Inject Provider<ApplicationRepository> applicationRepositoryProvider;
  @Inject Provider<ServiceRepository> serviceRepositoryProvider;

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(Bootstrap.class);
  }

  public void run(String[] args) throws Exception {
    final Config config = init(args);

    if (dryRun) {
      logger().warn("Running in dry-run mode; changes will only be logged, the database won't be modified.");
    }

    final Injector injector = Guice.createInjector(
        JongoModule.create(config.getConfig("oasis.mongo")),
        // TODO: store PKIs in DB to use a single subtree of the config
        AuthModule.create(config.getConfig("oasis.auth")
            .withFallback(config.withOnlyPath("oasis.conf-dir")))
    );

    injector.injectMembers(this);

    jongoService.start();
    try {
      createOpenIdConnectScopes();
      createKernelScopes();
      if (Strings.isNullOrEmpty(adminPassword)) {
        adminPassword = passwordGeneratorProvider.get().generate();
        logger().info("Generated password for {} account: {}", adminEmail, adminPassword);
      }
      String adminAccountId = createAdminUser();
      String oasisOrgId = createOasisOrganization(adminAccountId);
      String portalSecret = createPortal(oasisOrgId, adminAccountId);
      logger().info("Generated client_secret for {} instance: {}", ClientIds.PORTAL, portalSecret);
      String dcSecret = createDatacore(oasisOrgId, adminAccountId);
      logger().info("Generated client_secret for {} instance: {}", ClientIds.DATACORE, dcSecret);
    } finally {
      jongoService.stop();
    }
  }

  private void createOpenIdConnectScopes() {
    ScopeRepository scopeRepository = scopeRepositoryProvider.get();

    // TODO: I18N
    Scope openid = new Scope();
    openid.setLocal_id(Scopes.OPENID);
    openid.computeId();
    openid.getName().set(ULocale.ROOT, "Sign you in with your OASIS account");
    openid.getDescription().set(ULocale.ROOT, "The application will only know your account's internal identifier, no personal information will be shared.");
    scopeRepository.createOrUpdateScope(openid);

    Scope profile = new Scope();
    profile.setLocal_id(Scopes.PROFILE);
    profile.computeId();
    profile.getName().set(ULocale.ROOT, "Basic information about your profile");
    profile.getDescription().set(ULocale.ROOT, "This information includes your name, gender, birth date and picture.");
    scopeRepository.createOrUpdateScope(profile);

    Scope email = new Scope();
    email.setLocal_id(Scopes.EMAIL);
    email.computeId();
    email.getName().set(ULocale.ROOT, "Your email address");
    scopeRepository.createOrUpdateScope(email);

    Scope address = new Scope();
    address.setLocal_id(Scopes.ADDRESS);
    address.computeId();
    address.getName().set(ULocale.ROOT, "Your postal address");
    scopeRepository.createOrUpdateScope(address);

    Scope phone = new Scope();
    phone.setLocal_id(Scopes.PHONE);
    phone.computeId();
    phone.getName().set(ULocale.ROOT, "Your phone number");
    scopeRepository.createOrUpdateScope(phone);

    Scope offline = new Scope();
    offline.setLocal_id(Scopes.OFFLINE_ACCESS);
    offline.computeId();
    offline.getName().set(ULocale.ROOT, "Accessing all this information while you're not connected");
    offline.getDescription().set(ULocale.ROOT, "The application will be able to access your data even after you log out of OASIS.");
    scopeRepository.createOrUpdateScope(offline);
  }

  private void createKernelScopes() {
    // TODO!
  }

  private String createAdminUser() {
    UserAccount admin = new UserAccount();
    admin.setEmail_address(adminEmail);
    admin.setEmail_verified(true);
    admin.setNickname("Administrator");
    admin.setLocale(ULocale.getDefault());
    admin.setZoneinfo(TimeZone.getDefault().getID());
    admin = accountRepositoryProvider.get().createUserAccount(admin);
    credentialsServiceProvider.get().setPassword(ClientType.USER, admin.getId(), adminPassword);
    return admin.getId();
  }

  private String createOasisOrganization(String adminAccountId) {
    Organization oasis = new Organization();
    oasis.setName("OASIS");
    oasis.setType(Organization.Type.COMPANY);
    oasis.setStatus(Organization.Status.AVAILABLE);
    oasis = directoryRepositoryProvider.get().createOrganization(oasis);

    OrganizationMembership membership = new OrganizationMembership();
    membership.setAccountId(adminAccountId);
    membership.setOrganizationId(oasis.getId());
    membership.setAdmin(true);
    membership.setStatus(OrganizationMembership.Status.ACCEPTED);
    organizationMembershipRepositoryProvider.get().createOrganizationMembership(membership);

    return oasis.getId();
  }

  private String createPortal(String oasisOrgId, String adminAccountId) {
    Application app = new Application();
    app.getName().set(ULocale.ROOT, "OASIS Portal");
    app.setProvider_id(oasisOrgId);
    app.setVisible(false);
    app = applicationRepositoryProvider.get().createApplication(app);

    JongoAppInstance instance = new JongoAppInstance();
    instance.setId(ClientIds.PORTAL);
    instance.getName().set(ULocale.ROOT, "OASIS Portal");
    instance.setApplication_id(app.getId());
    instance.setStatus(AppInstance.InstantiationStatus.RUNNING);
    instance.setInstantiator_id(adminAccountId);
    for (String scopeId : new String[] { Scopes.OPENID, Scopes.PROFILE, Scopes.EMAIL, Scopes.ADDRESS, Scopes.PHONE, "datacore" }) {
      AppInstance.NeededScope neededScope = new AppInstance.NeededScope();
      neededScope.setScope_id(scopeId);
      instance.getNeeded_scopes().add(neededScope);
    }
    jongoProvider.get().getCollection(JongoAppInstanceRepository.COLLECTION_NAME).insert(instance);

    String clientSecret = passwordGeneratorProvider.get().generate();
    credentialsServiceProvider.get().setPassword(ClientType.PROVIDER, instance.getId(), clientSecret);

    Service service = new Service();
    service.setLocal_id("front");
    service.setInstance_id(instance.getId());
    service.setVisible(true); // we don't want filtering by ACL, portal will be filtered out by Market search
    service.setStatus(Service.Status.AVAILABLE);
    service.getName().set(ULocale.ROOT, "OASIS Portal");
    service.getRedirect_uris().add(portalRedirectUri);
    service.getPost_logout_redirect_uris().add(portalPostLogoutRedirectUri);
    serviceRepositoryProvider.get().createService(service);

    return clientSecret;
  }

  private String createDatacore(String oasisOrgId, String adminAccountId) {
    Application app = new Application();
    app.getName().set(ULocale.ROOT, "OASIS Datacore");
    app.setProvider_id(oasisOrgId);
    app.setVisible(false);
    app = applicationRepositoryProvider.get().createApplication(app);

    JongoAppInstance instance = new JongoAppInstance();
    instance.setId(ClientIds.DATACORE);
    instance.getName().set(ULocale.ROOT, "OASIS Datacore");
    instance.setApplication_id(app.getId());
    instance.setStatus(AppInstance.InstantiationStatus.RUNNING);
    instance.setInstantiator_id(adminAccountId);
    jongoProvider.get().getCollection(JongoAppInstanceRepository.COLLECTION_NAME).insert(instance);

    String clientSecret = passwordGeneratorProvider.get().generate();
    credentialsServiceProvider.get().setPassword(ClientType.PROVIDER, instance.getId(), clientSecret);

    // FIXME: we need dc-specific scopes
    Scope scope = new Scope();
    scope.setLocal_id("datacore");
    // Note: computeId must be called BEFORE setInstance_id fo the scope ID to be "datacore" (and not "dc:datacore")
    scope.computeId();
    scope.setInstance_id(instance.getId());
    scope.getName().set(ULocale.ROOT, "Datacore");
    scopeRepositoryProvider.get().createOrUpdateScope(scope);

    // XXX: do we need a service?

    return clientSecret;
  }
}
