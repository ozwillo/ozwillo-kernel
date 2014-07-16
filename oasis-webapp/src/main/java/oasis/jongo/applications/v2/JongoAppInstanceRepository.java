package oasis.jongo.applications.v2;

import java.util.List;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.QueryModifier;
import org.jongo.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.DBObject;
import com.mongodb.WriteResult;

import oasis.jongo.JongoBootstrapper;
import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;

public class JongoAppInstanceRepository implements AppInstanceRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(AppInstanceRepository.class);

  private final Jongo jongo;

  @Inject
  JongoAppInstanceRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public AppInstance createAppInstance(AppInstance instance) {
    JongoAppInstance jongoAppInstance = new JongoAppInstance(instance);
    getAppInstancesCollection().insert(jongoAppInstance);
    return jongoAppInstance;
  }

  @Override
  public AppInstance getAppInstance(String instanceId) {
    return getAppInstancesCollection()
        .findOne("{ id: #}", instanceId)
        .as(JongoAppInstance.class);
  }

  @Override
  public boolean instantiated(String instanceId, List<AppInstance.NeededScope> neededScopes) {
    AppInstance instance = getAppInstancesCollection()
        .findAndModify("{ id: #, status: # }", instanceId, AppInstance.InstantiationStatus.PENDING)
        .with("{ $set: { status: #, needed_scopes: # } }", AppInstance.InstantiationStatus.RUNNING, neededScopes)
        .projection("{ id: 1 }")
        .as(AppInstance.class);
    return instance != null;
  }

  @Override
  public boolean deletePendingInstance(String instanceId) {
    return getAppInstancesCollection()
        .remove("{ id: #, status: # }", instanceId, AppInstance.InstantiationStatus.PENDING)
        .getN() != 0;
  }

  @Override
  public void bootstrap() {
    getAppInstancesCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
  }

  private MongoCollection getAppInstancesCollection() {
    return jongo.getCollection("app_instances");
  }
}
