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
  public void bootstrap() {
    getServicesCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
  }

  private MongoCollection getServicesCollection() {
    return jongo.getCollection("services");
  }
}
