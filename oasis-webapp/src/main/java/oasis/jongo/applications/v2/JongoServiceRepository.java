package oasis.jongo.applications.v2;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oasis.jongo.JongoBootstrapper;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;

public class JongoServiceRepository implements ServiceRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(ServiceRepository.class);

  private final Jongo jongo;

  @Inject
  JongoServiceRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  private MongoCollection getServicesCollection() {
    return jongo.getCollection("services");
  }

  @Override
  public Iterable<Service> getVisibleServices() {
    return getServicesCollection().find("{ visible: true }").as(Service.class);
  }

  @Override
  public Service createService(Service application) {
    JongoService jongoService = new JongoService(application);
    getServicesCollection().insert(jongoService);
    return jongoService;
  }

  @Override
  public Service getService(String serviceId) {
    return getServicesCollection()
        .findOne("{ id: # }", serviceId)
        .as(JongoService.class);
  }

  @Override
  public Iterable<Service> getServicesOfInstance(String instanceId) {
    return getServicesCollection()
        .find("{ instance_id: # }", instanceId)
        .as(Service.class);
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
  public void bootstrap() {
    getServicesCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    getServicesCollection().ensureIndex("{ instance_id: 1, redirect_uris: 1 }", "{ unique: 1, sparse: 1 }");
    getServicesCollection().ensureIndex("{ instance_id: 1, post_logout_redirect_uris: 1 }", "{ unique: 1, sparse: 1 }");
  }
}
