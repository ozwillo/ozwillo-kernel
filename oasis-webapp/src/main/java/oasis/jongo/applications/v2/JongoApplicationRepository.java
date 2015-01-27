package oasis.jongo.applications.v2;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.DuplicateKeyException;

import oasis.jongo.JongoBootstrapper;
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;

public class JongoApplicationRepository implements ApplicationRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(ApplicationRepository.class);

  static final String APPLICATIONS_COLLECTION = "applications";

  private final Jongo jongo;

  @Inject JongoApplicationRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public Application getApplication(String applicationId) {
    return getApplicationsCollection()
        .findOne("{ id: # }", applicationId)
        .as(JongoApplication.class);
  }

  @Override
  public Application createApplication(Application application) {
    application = new JongoApplication(application);
    try {
      getApplicationsCollection().insert(application);
    } catch (DuplicateKeyException e) {
      return null;
    }
    return application;
  }

  @Override
  public void bootstrap() {
    getApplicationsCollection().ensureIndex("{ id : 1 }", "{ unique: 1 }");
  }

  private MongoCollection getApplicationsCollection() {
    return jongo.getCollection(APPLICATIONS_COLLECTION);
  }
}
