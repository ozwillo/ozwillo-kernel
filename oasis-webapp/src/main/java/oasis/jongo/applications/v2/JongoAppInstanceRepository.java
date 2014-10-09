package oasis.jongo.applications.v2;

import java.util.List;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.DuplicateKeyException;

import oasis.jongo.JongoBootstrapper;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;

public class JongoAppInstanceRepository implements AppInstanceRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(AppInstanceRepository.class);
  public static final String COLLECTION_NAME = "app_instances";

  private final Jongo jongo;

  @Inject
  JongoAppInstanceRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public AppInstance createAppInstance(AppInstance instance) {
    JongoAppInstance jongoAppInstance = new JongoAppInstance(instance);
    try {
      getAppInstancesCollection().insert(jongoAppInstance);
    } catch (DuplicateKeyException e) {
      return null;
    }
    return jongoAppInstance;
  }

  @Override
  public AppInstance getAppInstance(String instanceId) {
    return getAppInstancesCollection()
        .findOne("{ id: #}", instanceId)
        .as(JongoAppInstance.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<AppInstance> findByOrganizationId(String organizationId) {
    return (Iterable<AppInstance>) (Iterable<?>) getAppInstancesCollection()
        .find("{ provider_id: # }", organizationId)
        .as(JongoAppInstance.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<AppInstance> findPersonalInstancesByUserId(String userId) {
    return (Iterable<AppInstance>) (Iterable<?>) getAppInstancesCollection()
        .find("{ instantiator_id: #, provider_id: { $exists: 0 } }", userId)
        .as(JongoAppInstance.class);
  }

  @Override
  public AppInstance instantiated(String instanceId, List<AppInstance.NeededScope> neededScopes, String destruction_uri, String destruction_secret) {
    AppInstance instance = getAppInstancesCollection()
        .findAndModify("{ id: #, status: # }", instanceId, AppInstance.InstantiationStatus.PENDING)
        .with("{ $set: { status: #, needed_scopes: #, destruction_uri: #, destruction_secret: # } }",
            AppInstance.InstantiationStatus.RUNNING, neededScopes, destruction_uri, destruction_secret)
        .as(AppInstance.class);
    return instance;
  }

  @Override
  public boolean deleteInstance(String instanceId) {
    return getAppInstancesCollection()
        .remove("{ id: # }", instanceId)
        .getN() != 0;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<AppInstance> getInstancesForApplication(String applicationId) {
    return (Iterable<AppInstance>) (Iterable<?>) getAppInstancesCollection()
        .find("{ application_id: # }", applicationId)
        .as(JongoAppInstance.class);
  }

  @Override
  public void bootstrap() {
    getAppInstancesCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
  }

  private MongoCollection getAppInstancesCollection() {
    return jongo.getCollection(COLLECTION_NAME);
  }
}
