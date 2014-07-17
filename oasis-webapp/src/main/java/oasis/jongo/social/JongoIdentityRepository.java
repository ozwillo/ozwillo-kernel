package oasis.jongo.social;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.WriteResult;

import oasis.jongo.JongoBootstrapper;
import oasis.model.directory.DirectoryRepository;
import oasis.model.social.Identity;
import oasis.model.social.IdentityRepository;

public class JongoIdentityRepository implements IdentityRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(DirectoryRepository.class);

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
  public boolean updateIdentity(Identity identity) {
    JongoIdentity jongoIdentity = new JongoIdentity(identity);
    jongoIdentity.setId(identity.getId());
    jongoIdentity.setUpdatedAt(System.currentTimeMillis());

    WriteResult writeResult = getIdentityCollection().update("{id: #}", identity.getId()).with(jongoIdentity);
    if (writeResult.getN() == 0) {
      logger.warn("The identity {} does not exist.", identity.getId());
    }
    return writeResult.getN() > 0;
  }

  @Override
  public boolean deleteIdentity(String identityId) {
    WriteResult wr = getIdentityCollection().remove("{ id: # }", identityId);

    return wr.getN() != 0;
  }

  @Override
  public void bootstrap() {
    getIdentityCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    // XXX: we'd need to add other indexes but we'll refactor everything soon so wwe don't bother
  }

  private MongoCollection getIdentityCollection() {
    return jongo.getCollection("identity");
  }
}
