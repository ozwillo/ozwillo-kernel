package oasis.jongo.accounts;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.mongodb.DuplicateKeyException;

import oasis.jongo.JongoBootstrapper;
import oasis.model.InvalidVersionException;
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
  public UserAccount getUserAccountByEmail(String email) {
    return this.getAccountCollection()
        .findOne("{ email_address: # }", email)
        .as(JongoUserAccount.class);
  }

  @Override
  public UserAccount getUserAccountById(String id) {
    return this.getAccountCollection()
        .findOne("{ id: # }", id)
        .as(JongoUserAccount.class);
  }

  @Override
  public UserAccount createUserAccount(UserAccount user) {
    try {
      getAccountCollection().insert(new JongoUserAccount(user));
    } catch (DuplicateKeyException e) {
      return null;
    }
    return user;
  }

  @Override
  public UserAccount updateAccount(UserAccount account, long[] versions) throws InvalidVersionException {
    String id = account.getId();
    Preconditions.checkArgument(!Strings.isNullOrEmpty(id));
    // XXX: don't allow modifying the email address or phone number verified
    account.setEmail_address(null);
    account.setPhone_number_verified(null);
    account = getAccountCollection()
        .findAndModify("{ id: #, updated_at: { $in: # } }", id, versions)
        .with("{ $set: # }", new JongoUserAccount(account))
        .returnNew()
        .as(JongoUserAccount.class);
    if (account == null) {
      if (getAccountCollection().count("{ id: # }", id) != 0) {
        throw new InvalidVersionException("account", id);
      }
      return null;
    }
    return account;
  }

  @Override
  public void bootstrap() {
    getAccountCollection().ensureIndex("{ id : 1 }", "{ unique: 1 }");
    getAccountCollection().ensureIndex("{ email_address : 1 }", "{ unique: 1, sparse: 1 }");
  }
}

