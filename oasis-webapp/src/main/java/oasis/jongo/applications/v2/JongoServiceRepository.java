package oasis.jongo.applications.v2;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.primitives.Longs;
import com.mongodb.DuplicateKeyException;

import oasis.jongo.JongoBootstrapper;
import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.auth.AuthModule;

public class JongoServiceRepository implements ServiceRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(ServiceRepository.class);

  private final Jongo jongo;
  private final AuthModule.Settings settings;

  @Inject
  JongoServiceRepository(Jongo jongo, AuthModule.Settings settings) {
    this.jongo = jongo;
    this.settings = settings;
  }

  private MongoCollection getServicesCollection() {
    return jongo.getCollection("services");
  }

  @Override
  public Iterable<Service> getVisibleServices() {
    return getServicesCollection().find("{ visible: true }").as(Service.class);
  }

  @Override
  public Service createService(Service service) {
    JongoService jongoService = new JongoService(service);
    jongoService.initCreated();
    try {
      getServicesCollection().insert(jongoService);
    } catch (DuplicateKeyException e) {
      return null;
    }
    return jongoService;
  }

  @Override
  public Service getService(String serviceId) {
    return getServicesCollection()
        .findOne("{ id: # }", serviceId)
        .as(JongoService.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<Service> getServicesOfInstance(String instanceId) {
    return (Iterable<Service>) (Iterable<?>) getServicesCollection()
        .find("{ instance_id: # }", instanceId)
        .as(JongoService.class);
  }

  @Override
  public Service getServiceByRedirectUri(String instanceId, String redirect_uri) {
    return getServicesCollection()
        .findOne("{ instance_id: #, redirect_uris: # }", instanceId, redirect_uri)
        .as(JongoService.class);
  }

  @Override
  public Service getServiceByPostLogoutRedirectUri(String instanceId, String post_logout_redirect_uri) {
    return getServicesCollection()
        .findOne("{ instance_id: #, post_logout_redirect_uris: # }", instanceId, post_logout_redirect_uri)
        .as(JongoService.class);
  }

  @Override
  public boolean deleteService(String serviceId, long[] versions) throws InvalidVersionException {
    int n = getServicesCollection()
        .remove("{ id: #, modified: { $in: # } }", serviceId, Longs.asList(versions))
        .getN();

    if (n == 0) {
      if (getServicesCollection().count("{ id: # }", serviceId) > 0) {
        throw new InvalidVersionException("service", serviceId);
      }
      return false;
    }

    if (n > 1) {
      logger.error("Deleted {} services with ID {}, that shouldn't have happened", n, serviceId);
    }
    return true;
  }

  @Override
  public Service updateService(Service service, long[] versions) throws InvalidVersionException {
    String serviceId = service.getId();
    Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId));
    // Copy to get the modified field, then reset ID (not copied over) to make sure we won't generate a new one
    service = new JongoService(service);
    service.setId(serviceId);
    // XXX: don't allow updating those properties (should we return an error if attempted?)
    service.setLocal_id(null);
    service.setInstance_id(null);
    service.setProvider_id(null);
    // FIXME: allow unsetting properties
    service = getServicesCollection()
        .findAndModify("{ id: #, modified: { $in: # } }", serviceId, Longs.asList(versions))
        .with("{ $set: # }", service)
        .returnNew()
        .as(JongoService.class);
    if (service == null) {
      if (getServicesCollection().count("{ id: # }", serviceId) > 0) {
        throw new InvalidVersionException("service", serviceId);
      }
      return null;
    }
    return service;
  }

  @Override
  public int deleteServicesOfInstance(String instanceId) {
    return getServicesCollection()
        .remove("{ instance_id: # }", instanceId)
        .getN();
  }

  @Override
  public void bootstrap() {
    getServicesCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    getServicesCollection().ensureIndex("{ instance_id: 1, local_id: 1 }", "{ unique: 1, sparse: 1 }");

    getServicesCollection().ensureIndex("{ instance_id: 1, redirect_uris: 1 }", "{ unique: 1, sparse: 1 }");
    // We cannot make this index unique as that would rule out having several services,
    // for the same instance, without post_logout_redirect_uri at all.
    // XXX: we should probably move post_logout_redirect_uris to app_instances eventually.
    getServicesCollection().ensureIndex("{ instance_id: 1, post_logout_redirect_uris: 1 }", "{ sparse: 1 }");
  }
}
