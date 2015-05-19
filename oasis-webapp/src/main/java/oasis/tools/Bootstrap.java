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
      usage = "Portal's post_logout_redirect_uri")
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
    createOrUpdateOpenIdConnectScopes(scopeRepository);
  }

  static void createOrUpdateOpenIdConnectScopes(ScopeRepository scopeRepository) {
    Scope openid = new Scope();
    openid.setLocal_id(Scopes.OPENID);
    openid.computeId();
    openid.getName().set(ULocale.ROOT, "Sign you in with Ozwillo");
    openid.getName().set(ULocale.FRANCE, "Vous connecter avec Ozwillo");
    openid.getName().set(ULocale.ITALY, "Registrati in Ozwillo");
    openid.getName().set(ULocale.forLanguageTag("bg-BG"), "Влезте с Ozwillo");
    openid.getName().set(ULocale.forLanguageTag("ca-ES"), "Connectar-se a Ozwillo");
    openid.getName().set(ULocale.forLanguageTag("es-ES"), "Conectarse con Ozwillo");
    openid.getName().set(ULocale.forLanguageTag("tr-TR"), "Sizin için Ozwillo ile oturum başlatma");
    openid.getDescription().set(ULocale.ROOT, "No personal information will be shared through sign in.");
    openid.getDescription().set(ULocale.FRANCE, "Aucune information personnelle n'est partagée lors de la connexion");
    openid.getDescription().set(ULocale.ITALY, "Nessuna informazione personale verrà condivisa registrandosi");
    openid.getDescription().set(ULocale.forLanguageTag("bg-BG"), "Никакви лични данни няма да бъдат споделяни при влизане");
    openid.getDescription().set(ULocale.forLanguageTag("ca-ES"), "No hi ha informació personal a ser compartida a través del registre");
    openid.getDescription().set(ULocale.forLanguageTag("es-ES"), "No hay información personal a ser compartida a través del registro");
    openid.getDescription().set(ULocale.forLanguageTag("tr-TR"), "Oturum başlatma esnasında kişisel hiçbir veri paylaşılmayacaktır.");
    scopeRepository.createOrUpdateScope(openid);

    Scope profile = new Scope();
    profile.setLocal_id(Scopes.PROFILE);
    profile.computeId();
    profile.getName().set(ULocale.ROOT, "View your basic profile info");
    profile.getName().set(ULocale.FRANCE, "Connaître les informations de base de votre profil");
    profile.getName().set(ULocale.ITALY, "Guarda le informazioni base del tuo profilo");
    profile.getName().set(ULocale.forLanguageTag("bg-BG"), "Преглед на основната информация във вашия профил");
    profile.getName().set(ULocale.forLanguageTag("ca-ES"), "Veure la informació bàsica de perfil");
    profile.getName().set(ULocale.forLanguageTag("es-ES"), "Ver la información básica de perfil");
    profile.getName().set(ULocale.forLanguageTag("tr-TR"), "Temel profil bilgilerinizi görüntüleme");
    profile.getDescription().set(ULocale.ROOT, "This information includes your name, gender, birth date and picture.");
    profile.getDescription().set(ULocale.FRANCE, "Accéder à vos nom, sexe, date de naissance, photo de profil");
    profile.getDescription().set(ULocale.ITALY, "Queste informazioni includono il tuo nome, sesso, data di nascita e foto");
    profile.getDescription().set(ULocale.forLanguageTag("bg-BG"), "Тази информация вклюва вашето име, пол, дата на раждане и изображение");
    profile.getDescription().set(ULocale.forLanguageTag("ca-ES"), "Aquesta informació inclou el seu nom, sexe, data de naixement i imatge");
    profile.getDescription().set(ULocale.forLanguageTag("es-ES"), "Esta información incluye su nombre, sexo, fecha de nacimiento e imagen");
    profile.getDescription().set(ULocale.forLanguageTag("tr-TR"), "Bu bilgiler, isminizi, cinsiyetinizi, doğum tarihinizi ve resminizi içerir.");
    scopeRepository.createOrUpdateScope(profile);

    Scope email = new Scope();
    email.setLocal_id(Scopes.EMAIL);
    email.computeId();
    email.getName().set(ULocale.ROOT, "View your email address");
    email.getName().set(ULocale.FRANCE, "Connaître votre adresse e-mail");
    email.getName().set(ULocale.ITALY, "Guarda il tuo indirizo email");
    email.getName().set(ULocale.forLanguageTag("bg-BG"), "Преглед на собствения имейл-адрес");
    email.getName().set(ULocale.forLanguageTag("ca-ES"), "Veure el seu correu electrònic");
    email.getName().set(ULocale.forLanguageTag("es-ES"), "Ver su correo electrónico");
    email.getName().set(ULocale.forLanguageTag("tr-TR"), "eposta adresinizi görüntüleme");
    email.getDescription().set(ULocale.ROOT, "View the email address associated with your account.");
    email.getDescription().set(ULocale.FRANCE, "Accéder à l'adresse e-mail associée à votre compte");
    email.getDescription().set(ULocale.ITALY, "Guarda l'indirizzo email associato al tuo account");
    email.getDescription().set(ULocale.forLanguageTag("bg-BG"), "Преглед на свързания с вашия акаунт имейл адрес");
    email.getDescription().set(ULocale.forLanguageTag("ca-ES"), "Veure el correu electrònic associat amb el seu compte");
    email.getDescription().set(ULocale.forLanguageTag("es-ES"), "Ver el correo electrónico asociado a su cuenta");
    email.getDescription().set(ULocale.forLanguageTag("tr-TR"), "Hesabınız ile ilişkilendirilmiş olan eposta adresinizi görüntüleme");
    scopeRepository.createOrUpdateScope(email);

    Scope address = new Scope();
    address.setLocal_id(Scopes.ADDRESS);
    address.computeId();
    address.getName().set(ULocale.ROOT, "View your postal address");
    address.getName().set(ULocale.FRANCE, "Connaître votre adresse postale");
    address.getName().set(ULocale.ITALY, "Guarda il tuo indirizzo postale");
    address.getName().set(ULocale.forLanguageTag("bg-BG"), "Преглед на вашия пощенски адрес");
    address.getName().set(ULocale.forLanguageTag("ca-ES"), "Veure la seva adreça");
    address.getName().set(ULocale.forLanguageTag("es-ES"), "Ver su dirección");
    address.getName().set(ULocale.forLanguageTag("tr-TR"), "Posta adresinizi görüntüleme");
    address.getDescription().set(ULocale.ROOT, "View the postal address associated with your account.");
    address.getDescription().set(ULocale.FRANCE, "Accéder à l'adresse postale associée à votre compte");
    address.getDescription().set(ULocale.ITALY, "Guarda l'indirizzo postale associato al il tuo account");
    address.getDescription().set(ULocale.forLanguageTag("bg-BG"), "Преглед на пощенския адрес, свързан с вашия акаунт");
    address.getDescription().set(ULocale.forLanguageTag("ca-ES"), "Veure l’adreça associada amb el seu compte");
    address.getDescription().set(ULocale.forLanguageTag("es-ES"), "Ver la dirección asociada a su cuenta");
    address.getDescription().set(ULocale.forLanguageTag("tr-TR"), "Hesabınız ile ilişkilendirilmiş posta adresinizi görüntüleme");
    scopeRepository.createOrUpdateScope(address);

    Scope phone = new Scope();
    phone.setLocal_id(Scopes.PHONE);
    phone.computeId();
    phone.getName().set(ULocale.ROOT, "View your phone number");
    phone.getName().set(ULocale.FRANCE, "Connaître votre numéro de téléphone");
    phone.getName().set(ULocale.ITALY, "Guarda il tuo numero di telefono");
    phone.getName().set(ULocale.forLanguageTag("bg-BG"), "Преглед на вашия телефонен номер");
    phone.getName().set(ULocale.forLanguageTag("ca-ES"), "Veure el seu número de telèfon");
    phone.getName().set(ULocale.forLanguageTag("es-ES"), "Ver su número de teléfono");
    phone.getName().set(ULocale.forLanguageTag("tr-TR"), "Telefon numaranızı görüntüleme");
    phone.getDescription().set(ULocale.ROOT, "View the phone number associated with your account.");
    phone.getDescription().set(ULocale.FRANCE, "Accéder au numéro de téléphone associé à votre compte");
    phone.getDescription().set(ULocale.ITALY, "Guarda il numero di telefono associato al tuo account");
    phone.getDescription().set(ULocale.forLanguageTag("bg-BG"), "Преглед на телефонния номер, свързан с вашия акаунт");
    phone.getDescription().set(ULocale.forLanguageTag("ca-ES"), "Veure el número de telèfon associat amb el seu compte");
    phone.getDescription().set(ULocale.forLanguageTag("es-ES"), "Ver el número de teléfono asociado a su cuenta");
    phone.getDescription().set(ULocale.forLanguageTag("tr-TR"), "Hesabınız ile ilişkilendirilmiş telefon numaranızı görüntüleme");
    scopeRepository.createOrUpdateScope(phone);

    Scope offline = new Scope();
    offline.setLocal_id(Scopes.OFFLINE_ACCESS);
    offline.computeId();
    offline.getName().set(ULocale.ROOT, "Access your data while you're not connected");
    offline.getName().set(ULocale.FRANCE, "Accéder à vos données quand vous n'êtes pas connecté");
    offline.getName().set(ULocale.ITALY, "Accedi ai tuoi dati quando non sei connesso");
    offline.getName().set(ULocale.forLanguageTag("bg-BG"), "Достъп до вашите данни когато не сте свързан");
    offline.getName().set(ULocale.forLanguageTag("ca-ES"), "Accedeixi a les seves dades tot i no estar connectat");
    offline.getName().set(ULocale.forLanguageTag("es-ES"), "Acceda a sus datos aunque no este conectado");
    offline.getName().set(ULocale.forLanguageTag("tr-TR"), "Bağlı olmadığınız esnada verilerinize erişebilmek");
    offline.getDescription().set(ULocale.ROOT, "The application will be able to access your data even after you log out of Ozwillo.");
    offline.getDescription().set(ULocale.FRANCE, "Accéder à vos données même après vous être déconnecté(e) d'Ozwillo");
    offline.getDescription().set(ULocale.ITALY, "Accedi ai tuoi dati anche dopo la disconnessione da Ozwillo");
    offline.getDescription().set(ULocale.forLanguageTag("bg-BG"), "Достъп до вашите данни дори и след излизането ви от Ozwillo");
    offline.getDescription().set(ULocale.forLanguageTag("ca-ES"), "Accedeixi a les seves dades fins i tot després de tancar la sessió de Ozwillo");
    offline.getDescription().set(ULocale.forLanguageTag("es-ES"), "Acceda a sus datos incluso después de cerrar la sesión de Ozwillo");
    offline.getDescription().set(ULocale.forLanguageTag("tr-TR"), "Ozwillo'da oturum sonlandırma yaptıktan sonra dahi verilerinize erişebilmek");
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
    service.getName().set(ULocale.ROOT, "Ozwillo Portal");
    service.getRedirect_uris().add(portalRedirectUri);
    service.getPost_logout_redirect_uris().add(portalPostLogoutRedirectUri);
    serviceRepositoryProvider.get().createService(service);

    return clientSecret;
  }

  private String createDatacore(String oasisOrgId, String adminAccountId) {
    Application app = new Application();
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
