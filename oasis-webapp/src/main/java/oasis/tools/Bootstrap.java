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
package oasis.tools;

import javax.inject.Inject;
import javax.inject.Provider;

import org.jongo.Jongo;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ibm.icu.util.ULocale;
import com.typesafe.config.Config;

import oasis.auth.AuthModule;
import oasis.jongo.JongoService;
import oasis.jongo.applications.v2.JongoAppInstance;
import oasis.jongo.applications.v2.JongoAppInstanceRepository;
import oasis.jongo.applications.v2.JongoServiceRepository;
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

  @Option(name = "-pr", aliases = "--portal-redirect-uri", required = true,
      usage = "Portal's redirect_uri")
  private String portalRedirectUri;

  @Option(name = "-plr", aliases = "--portal-post-logout-redirect-uri", required = true,
      usage = "Portal's post_logout_redirect_uri")
  private String portalPostLogoutRedirectUri;

  @Option(name = "-dr", aliases = "--datacore-redirect-uri", required = true,
      usage = "Datacore Playground's redirect_uri")
  private String datacoreRedirectUri;

  @Option(name = "-ds", aliases = "--datacore-service-uri", required = true,
      usage = "Datacore Playground's service_uri")
  private String datacoreServiceUri;

  @Option(name = "-di", aliases = "--datacore-icon", required = true,
      usage = "Datacore Playground's icon")
  private String datacoreIcon;

  @Option(name = "-der", aliases = "--dcexporter-redirect-uri",
      usage = "Datacore Exporter's redirect_uri")
  private String dcexporterRedirectUri;

  @Option(name = "-des", aliases = "--dcexporter-service-uri",
      usage = "Datacore Exporter's service_uri")
  private String dcexporterServiceUri;

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
        new AbstractModule() {
          @Override
          protected void configure() {
            // ServiceRepository is generally bound through CatalogModule
            bind(ServiceRepository.class).to(JongoServiceRepository.class);
          }
        },
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
      if (!Strings.isNullOrEmpty(dcexporterRedirectUri) && !Strings.isNullOrEmpty(dcexporterServiceUri)) {
        String dcExporterSecret = createDcExporter(oasisOrgId, adminAccountId);
        logger().info("Generated client_secret for {} instance: {}", ClientIds.DCEXPORTER, dcExporterSecret);
      }
    } finally {
      jongoService.stop();
    }
  }

  private void createOpenIdConnectScopes() {
    ScopeRepository scopeRepository = scopeRepositoryProvider.get();
    createOrUpdateOpenIdConnectScopes(scopeRepository);
  }

  static void createOrUpdateOpenIdConnectScopes(ScopeRepository scopeRepository) {
    Scope openid = new Scope();
    openid.setLocal_id(Scopes.OPENID);
    openid.computeId();
    openid.getName().set(ULocale.ROOT, "Sign you in with Ozwillo");
    openid.getName().set(ULocale.FRENCH, "Vous connecter avec Ozwillo");
    openid.getName().set(ULocale.ITALIAN, "Registrati in Ozwillo");
    openid.getName().set(ULocale.forLanguageTag("bg"), "Влезте с Ozwillo");
    openid.getName().set(ULocale.forLanguageTag("ca"), "Connectar-se a Ozwillo");
    openid.getName().set(ULocale.forLanguageTag("es"), "Conectarse con Ozwillo");
    openid.getName().set(ULocale.forLanguageTag("tr"), "Sizin için Ozwillo ile oturum başlatma");
    openid.getDescription().set(ULocale.ROOT, "No personal information will be shared through sign in.");
    openid.getDescription().set(ULocale.FRENCH, "Aucune information personnelle n'est partagée lors de la connexion");
    openid.getDescription().set(ULocale.ITALIAN, "Nessuna informazione personale verrà condivisa registrandosi");
    openid.getDescription().set(ULocale.forLanguageTag("bg"), "Никакви лични данни няма да бъдат споделяни при влизане");
    openid.getDescription().set(ULocale.forLanguageTag("ca"), "No hi ha informació personal a ser compartida a través del registre");
    openid.getDescription().set(ULocale.forLanguageTag("es"), "No hay información personal a ser compartida a través del registro");
    openid.getDescription().set(ULocale.forLanguageTag("tr"), "Oturum başlatma esnasında kişisel hiçbir veri paylaşılmayacaktır.");
    scopeRepository.createOrUpdateScope(openid);

    Scope profile = new Scope();
    profile.setLocal_id(Scopes.PROFILE);
    profile.computeId();
    profile.getName().set(ULocale.ROOT, "View your basic profile info");
    profile.getName().set(ULocale.FRENCH, "Connaître les informations de base de votre profil");
    profile.getName().set(ULocale.ITALIAN, "Guarda le informazioni base del tuo profilo");
    profile.getName().set(ULocale.forLanguageTag("bg"), "Преглед на основната информация във вашия профил");
    profile.getName().set(ULocale.forLanguageTag("ca"), "Veure la informació bàsica de perfil");
    profile.getName().set(ULocale.forLanguageTag("es"), "Ver la información básica de perfil");
    profile.getName().set(ULocale.forLanguageTag("tr"), "Temel profil bilgilerinizi görüntüleme");
    profile.getDescription().set(ULocale.ROOT, "This information includes your name, gender, birth date and picture.");
    profile.getDescription().set(ULocale.FRENCH, "Accéder à vos nom, sexe, date de naissance, photo de profil");
    profile.getDescription().set(ULocale.ITALIAN, "Queste informazioni includono il tuo nome, sesso, data di nascita e foto");
    profile.getDescription().set(ULocale.forLanguageTag("bg"), "Тази информация вклюва вашето име, пол, дата на раждане и изображение");
    profile.getDescription().set(ULocale.forLanguageTag("ca"), "Aquesta informació inclou el seu nom, sexe, data de naixement i imatge");
    profile.getDescription().set(ULocale.forLanguageTag("es"), "Esta información incluye su nombre, sexo, fecha de nacimiento e imagen");
    profile.getDescription().set(ULocale.forLanguageTag("tr"), "Bu bilgiler, isminizi, cinsiyetinizi, doğum tarihinizi ve resminizi içerir.");
    scopeRepository.createOrUpdateScope(profile);

    Scope email = new Scope();
    email.setLocal_id(Scopes.EMAIL);
    email.computeId();
    email.getName().set(ULocale.ROOT, "View your email address");
    email.getName().set(ULocale.FRENCH, "Connaître votre adresse e-mail");
    email.getName().set(ULocale.ITALIAN, "Guarda il tuo indirizo email");
    email.getName().set(ULocale.forLanguageTag("bg"), "Преглед на собствения имейл-адрес");
    email.getName().set(ULocale.forLanguageTag("ca"), "Veure el seu correu electrònic");
    email.getName().set(ULocale.forLanguageTag("es"), "Ver su correo electrónico");
    email.getName().set(ULocale.forLanguageTag("tr"), "eposta adresinizi görüntüleme");
    email.getDescription().set(ULocale.ROOT, "View the email address associated with your account.");
    email.getDescription().set(ULocale.FRENCH, "Accéder à l'adresse e-mail associée à votre compte");
    email.getDescription().set(ULocale.ITALIAN, "Guarda l'indirizzo email associato al tuo account");
    email.getDescription().set(ULocale.forLanguageTag("bg"), "Преглед на свързания с вашия акаунт имейл адрес");
    email.getDescription().set(ULocale.forLanguageTag("ca"), "Veure el correu electrònic associat amb el seu compte");
    email.getDescription().set(ULocale.forLanguageTag("es"), "Ver el correo electrónico asociado a su cuenta");
    email.getDescription().set(ULocale.forLanguageTag("tr"), "Hesabınız ile ilişkilendirilmiş olan eposta adresinizi görüntüleme");
    scopeRepository.createOrUpdateScope(email);

    Scope address = new Scope();
    address.setLocal_id(Scopes.ADDRESS);
    address.computeId();
    address.getName().set(ULocale.ROOT, "View your postal address");
    address.getName().set(ULocale.FRENCH, "Connaître votre adresse postale");
    address.getName().set(ULocale.ITALIAN, "Guarda il tuo indirizzo postale");
    address.getName().set(ULocale.forLanguageTag("bg"), "Преглед на вашия пощенски адрес");
    address.getName().set(ULocale.forLanguageTag("ca"), "Veure la seva adreça");
    address.getName().set(ULocale.forLanguageTag("es"), "Ver su dirección");
    address.getName().set(ULocale.forLanguageTag("tr"), "Posta adresinizi görüntüleme");
    address.getDescription().set(ULocale.ROOT, "View the postal address associated with your account.");
    address.getDescription().set(ULocale.FRENCH, "Accéder à l'adresse postale associée à votre compte");
    address.getDescription().set(ULocale.ITALIAN, "Guarda l'indirizzo postale associato al il tuo account");
    address.getDescription().set(ULocale.forLanguageTag("bg"), "Преглед на пощенския адрес, свързан с вашия акаунт");
    address.getDescription().set(ULocale.forLanguageTag("ca"), "Veure l’adreça associada amb el seu compte");
    address.getDescription().set(ULocale.forLanguageTag("es"), "Ver la dirección asociada a su cuenta");
    address.getDescription().set(ULocale.forLanguageTag("tr"), "Hesabınız ile ilişkilendirilmiş posta adresinizi görüntüleme");
    scopeRepository.createOrUpdateScope(address);

    Scope phone = new Scope();
    phone.setLocal_id(Scopes.PHONE);
    phone.computeId();
    phone.getName().set(ULocale.ROOT, "View your phone number");
    phone.getName().set(ULocale.FRENCH, "Connaître votre numéro de téléphone");
    phone.getName().set(ULocale.ITALIAN, "Guarda il tuo numero di telefono");
    phone.getName().set(ULocale.forLanguageTag("bg"), "Преглед на вашия телефонен номер");
    phone.getName().set(ULocale.forLanguageTag("ca"), "Veure el seu número de telèfon");
    phone.getName().set(ULocale.forLanguageTag("es"), "Ver su número de teléfono");
    phone.getName().set(ULocale.forLanguageTag("tr"), "Telefon numaranızı görüntüleme");
    phone.getDescription().set(ULocale.ROOT, "View the phone number associated with your account.");
    phone.getDescription().set(ULocale.FRENCH, "Accéder au numéro de téléphone associé à votre compte");
    phone.getDescription().set(ULocale.ITALIAN, "Guarda il numero di telefono associato al tuo account");
    phone.getDescription().set(ULocale.forLanguageTag("bg"), "Преглед на телефонния номер, свързан с вашия акаунт");
    phone.getDescription().set(ULocale.forLanguageTag("ca"), "Veure el número de telèfon associat amb el seu compte");
    phone.getDescription().set(ULocale.forLanguageTag("es"), "Ver el número de teléfono asociado a su cuenta");
    phone.getDescription().set(ULocale.forLanguageTag("tr"), "Hesabınız ile ilişkilendirilmiş telefon numaranızı görüntüleme");
    scopeRepository.createOrUpdateScope(phone);

    Scope offline = new Scope();
    offline.setLocal_id(Scopes.OFFLINE_ACCESS);
    offline.computeId();
    offline.getName().set(ULocale.ROOT, "Access your data while you're not connected");
    offline.getName().set(ULocale.FRENCH, "Accéder à vos données quand vous n'êtes pas connecté");
    offline.getName().set(ULocale.ITALIAN, "Accedi ai tuoi dati quando non sei connesso");
    offline.getName().set(ULocale.forLanguageTag("bg"), "Достъп до вашите данни когато не сте свързан");
    offline.getName().set(ULocale.forLanguageTag("ca"), "Accedeixi a les seves dades tot i no estar connectat");
    offline.getName().set(ULocale.forLanguageTag("es"), "Acceda a sus datos aunque no este conectado");
    offline.getName().set(ULocale.forLanguageTag("tr"), "Bağlı olmadığınız esnada verilerinize erişebilmek");
    offline.getDescription().set(ULocale.ROOT, "The application will be able to access your data even after you log out of Ozwillo.");
    offline.getDescription().set(ULocale.FRENCH, "Accéder à vos données même après vous être déconnecté(e) d'Ozwillo");
    offline.getDescription().set(ULocale.ITALIAN, "Accedi ai tuoi dati anche dopo la disconnessione da Ozwillo");
    offline.getDescription().set(ULocale.forLanguageTag("bg"), "Достъп до вашите данни дори и след излизането ви от Ozwillo");
    offline.getDescription().set(ULocale.forLanguageTag("ca"), "Accedeixi a les seves dades fins i tot després de tancar la sessió de Ozwillo");
    offline.getDescription().set(ULocale.forLanguageTag("es"), "Acceda a sus datos incluso después de cerrar la sesión de Ozwillo");
    offline.getDescription().set(ULocale.forLanguageTag("tr"), "Ozwillo'da oturum sonlandırma yaptıktan sonra dahi verilerinize erişebilmek");
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
    admin = accountRepositoryProvider.get().createUserAccount(admin, true);
    credentialsServiceProvider.get().setPassword(ClientType.USER, admin.getId(), adminPassword);
    return admin.getId();
  }

  private String createOasisOrganization(String adminAccountId) {
    Organization oasis = new Organization();
    oasis.setName("Ozwillo");
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
    app.setId(ClientIds.PORTAL);
    app.getName().set(ULocale.ROOT, "Ozwillo Portal");
    app.setProvider_id(oasisOrgId);
    app.setVisible(false);
    app = applicationRepositoryProvider.get().createApplication(app);

    JongoAppInstance instance = new JongoAppInstance();
    instance.setId(ClientIds.PORTAL);
    instance.getName().set(ULocale.ROOT, "Ozwillo Portal");
    instance.setApplication_id(app.getId());
    instance.setStatus(AppInstance.InstantiationStatus.RUNNING);
    instance.setInstantiator_id(adminAccountId);
    instance.setProvider_id(oasisOrgId);
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
    service.setProvider_id(instance.getProvider_id());
    service.setVisibility(Service.Visibility.HIDDEN);
    service.setAccess_control(Service.AccessControl.ANYONE);
    service.setStatus(Service.Status.AVAILABLE);
    service.getName().set(ULocale.ROOT, "Ozwillo Portal");
    service.getRedirect_uris().add(portalRedirectUri);
    service.getPost_logout_redirect_uris().add(portalPostLogoutRedirectUri);
    serviceRepositoryProvider.get().createService(service);

    return clientSecret;
  }

  private String createDatacore(String oasisOrgId, String adminAccountId) {
    Application app = new Application();
    app.setId(ClientIds.DATACORE);
    app.getName().set(ULocale.ROOT, "Ozwillo Datacore");
    app.setProvider_id(oasisOrgId);
    app.setVisible(false);
    app = applicationRepositoryProvider.get().createApplication(app);

    JongoAppInstance instance = new JongoAppInstance();
    instance.setId(ClientIds.DATACORE);
    instance.getName().set(ULocale.ROOT, "Ozwillo Datacore");
    instance.setApplication_id(app.getId());
    instance.setStatus(AppInstance.InstantiationStatus.RUNNING);
    instance.setInstantiator_id(adminAccountId);
    instance.setProvider_id(oasisOrgId);
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

    Service service = new Service();
    service.setLocal_id("playground");
    service.setInstance_id(instance.getId());
    service.setProvider_id(instance.getProvider_id());
    service.setVisibility(Service.Visibility.HIDDEN);
    service.setAccess_control(Service.AccessControl.RESTRICTED);
    service.setStatus(Service.Status.AVAILABLE);
    service.getName().set(ULocale.ROOT, "Ozwillo Datacore Playground");
    service.getRedirect_uris().add(datacoreRedirectUri);
    service.setService_uri(datacoreServiceUri);
    service.getIcon().set(ULocale.ROOT, datacoreIcon);
    serviceRepositoryProvider.get().createService(service);

    return clientSecret;
  }

  private String createDcExporter(String oasisOrgId, String adminAccountId) {
    Application app = new Application();
    app.getName().set(ULocale.ROOT, "Ozwillo Datacore Exporter");
    app.setProvider_id(oasisOrgId);
    app.setVisible(false);
    app = applicationRepositoryProvider.get().createApplication(app);

    JongoAppInstance instance = new JongoAppInstance();
    instance.setId(ClientIds.DCEXPORTER);
    instance.getName().set(ULocale.ROOT, "Ozwillo Datacore Exporter");
    instance.setApplication_id(app.getId());
    instance.setStatus(AppInstance.InstantiationStatus.RUNNING);
    instance.setInstantiator_id(adminAccountId);
    instance.setProvider_id(oasisOrgId);
    for (String scopeId : new String[] { Scopes.OPENID, Scopes.PROFILE, Scopes.EMAIL, "datacore" }) {
      AppInstance.NeededScope neededScope = new AppInstance.NeededScope();
      neededScope.setScope_id(scopeId);
      instance.getNeeded_scopes().add(neededScope);
    }
    jongoProvider.get().getCollection(JongoAppInstanceRepository.COLLECTION_NAME).insert(instance);

    String clientSecret = passwordGeneratorProvider.get().generate();
    credentialsServiceProvider.get().setPassword(ClientType.PROVIDER, instance.getId(), clientSecret);

    Service service = new Service();
    service.setLocal_id("dcexporter");
    service.setInstance_id(instance.getId());
    service.setProvider_id(instance.getProvider_id());
    service.setVisibility(Service.Visibility.HIDDEN);
    service.setAccess_control(Service.AccessControl.RESTRICTED);
    service.setStatus(Service.Status.AVAILABLE);
    service.getName().set(ULocale.ROOT, "Ozwillo Datacore Exporter");
    service.getRedirect_uris().add(dcexporterRedirectUri);
    service.setService_uri(dcexporterServiceUri);
    serviceRepositoryProvider.get().createService(service);

    return clientSecret;

  }
}
