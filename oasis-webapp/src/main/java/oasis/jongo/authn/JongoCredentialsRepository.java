package oasis.jongo.authn;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import oasis.model.authn.ClientType;
import oasis.model.authn.Credentials;
import oasis.model.authn.CredentialsRepository;

public class JongoCredentialsRepository implements CredentialsRepository {
  private final Jongo jongo;

  @Inject
  JongoCredentialsRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  protected MongoCollection getCredentialsCollection() {
    return jongo.getCollection("credentials");
  }

  @Override
  public Credentials saveCredentials(ClientType type, String id, byte[] hash, byte[] salt) {
    return getCredentialsCollection().findAndModify("{clientType:#,id:#}", type, id)
        .upsert()
        .returnNew()
        .with("{hash:#,salt:#}", hash, salt)
        .as(Credentials.class);
  }

  @Override
  public Credentials getCredentials(ClientType type, String id) {
    return getCredentialsCollection().findOne("{clientType:#,id:#}", type, id).as(Credentials.class);
  }
}
