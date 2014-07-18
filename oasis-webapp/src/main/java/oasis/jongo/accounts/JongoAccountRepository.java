package oasis.jongo.accounts;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    getAccountCollection().insert(user);
    return user;
  }

  @Override
  public void updatePassword(String accountId, String passwordHash, String passwordSalt) {
    WriteResult writeResult = getAccountCollection()
        .update("{ id: # }", accountId)
        .with("{ $set: { password: #, passwordSalt: # } }", passwordHash, passwordSalt);
    if (writeResult.getN() > 1) {
      logger.error("More than one account provider with id: {}", accountId);
    } else if (writeResult.getN() < 1) {
      logger.error("The account {} doesn't exist.", accountId);
    }
  }

  @Override
  public void bootstrap() {
    getAccountCollection().ensureIndex("{ id : 1 }", "{ unique: 1 }");
    getAccountCollection().ensureIndex("{ emailAddress : 1 }", "{ unique: 1, sparse: 1 }");
  }
}

