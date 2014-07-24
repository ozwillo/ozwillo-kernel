package oasis.jongo.accounts;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.DuplicateKeyException;
import com.mongodb.WriteResult;

import oasis.jongo.JongoBootstrapper;
import oasis.model.accounts.Account;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;

public class JongoAccountRepository implements AccountRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(JongoAccountRepository.class);

  private final Jongo jongo;

  @Inject
  JongoAccountRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  protected MongoCollection getAccountCollection() {
    return jongo.getCollection("account");
  }

  @Override
  public Account getAccount(String id) {
    return this.getAccountCollection()
        .findOne("{id:#}", id)
        .projection("{tokens: 0, authorizedScopes: 0}")
        .as(Account.class);
  }

  @Override
  public UserAccount getUserAccountByEmail(String email) {
    return this.getAccountCollection()
        .findOne("{emailAddress:#}", email)
        .projection("{tokens: 0, authorizedScopes: 0}")
        .as(UserAccount.class);
  }

  @Override
  public UserAccount getUserAccountById(String id) {
    return this.getAccountCollection()
        .findOne("{id:#}", id)
        .projection("{tokens: 0, authorizedScopes: 0}")
        .as(UserAccount.class);
  }

  @Override
  public UserAccount createUserAccount(UserAccount user) {
    user.setUpdatedAt(System.currentTimeMillis());
    try {
      getAccountCollection().insert(user);
    } catch (DuplicateKeyException e) {
      // Verify that the username was not used before
      return null;
    }
    return user;
  }

  @Override
  public void bootstrap() {
    getAccountCollection().ensureIndex("{ id : 1 }", "{ unique: 1 }");
    getAccountCollection().ensureIndex("{ emailAddress : 1 }", "{ unique: 1, sparse: 1 }");
  }
}

