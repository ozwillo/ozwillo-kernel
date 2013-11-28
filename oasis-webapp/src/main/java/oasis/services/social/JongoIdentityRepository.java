package oasis.services.social;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import oasis.model.social.Identity;
import oasis.model.social.IdentityRepository;

public class JongoIdentityRepository implements IdentityRepository {
  private final Jongo jongo;

  @Inject JongoIdentityRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public Identity getIdentity(String identityId) {
    return getIdentityCollection().findOne("{id:#}", identityId).as(Identity.class);
  }

  private MongoCollection getIdentityCollection() {
    return jongo.getCollection("identity");
  }
}
