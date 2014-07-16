package oasis.jongo.applications.v2;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oasis.jongo.JongoBootstrapper;
import oasis.model.applications.v2.Application;
import oasis.model.applications.v2.ApplicationRepository;

public class JongoApplicationRepository implements ApplicationRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(ApplicationRepository.class);

  private final Jongo jongo;

  @Inject JongoApplicationRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public Iterable<Application> getVisibleApplications() {
    return getApplicationsCollection().find("{ visible: true }").as(Application.class);
  }

  @Override
  public Application getApplication(String applicationId) {
    return getApplicationsCollection()
        .findOne("{ id: # }", applicationId)
        .as(JongoApplication.class);
  }

  @Override
  public void bootstrap() {
    getApplicationsCollection().ensureIndex("{ id : 1 }", "{ unique: 1 }");
  }

  private MongoCollection getApplicationsCollection() {
    return jongo.getCollection("applications");
  }
}
