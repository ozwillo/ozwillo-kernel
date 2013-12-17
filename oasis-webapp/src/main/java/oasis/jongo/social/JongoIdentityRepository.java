package oasis.jongo.social;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import com.mongodb.WriteResult;

import oasis.model.social.Identity;
import oasis.model.social.IdentityRepository;

public class JongoIdentityRepository implements IdentityRepository {
  private final Jongo jongo;

  @Inject
  JongoIdentityRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public Identity getIdentity(String identityId) {
    return getIdentityCollection().findOne("{id:#}", identityId).as(JongoIdentity.class);
  }

  @Override
  public Identity createIdentity(Identity identity) {
    JongoIdentity jongoIdentity = new JongoIdentity(identity);
    jongoIdentity.setUpdatedAt(System.currentTimeMillis());
    getIdentityCollection().insert(jongoIdentity);
    return jongoIdentity;
  }

  @Override
  public boolean deleteIdentity(String identityId) {
    WriteResult wr = getIdentityCollection().remove("{ id: # }", identityId);

    return wr.getN() != 0;
  }

  private MongoCollection getIdentityCollection() {
    return jongo.getCollection("identity");
  }
}
