package oasis.jongo.accounts;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.primitives.Longs;
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
    // XXX: accounts aren't activated until you verify the e-mail address
    return this.getAccountCollection()
        .findOne("{ email_address: #, email_verified: true }", email)
        .as(JongoUserAccount.class);
  }

  @Override
  public UserAccount getUserAccountById(String id) {
    // XXX: accounts aren't activated until you verify the e-mail address
    return this.getAccountCollection()
        .findOne("{ id: #, email_verified: true }", id)
        .as(JongoUserAccount.class);
  }

  @Override
  public UserAccount createUserAccount(UserAccount user) {
    JongoUserAccount jongoUserAccount = new JongoUserAccount(user);
    try {
      getAccountCollection().insert(jongoUserAccount);
    } catch (DuplicateKeyException e) {
      return null;
    }
    return jongoUserAccount;
  }

  @Override
  public UserAccount updateAccount(UserAccount account, long[] versions) throws InvalidVersionException {
    String id = account.getId();
    Preconditions.checkArgument(!Strings.isNullOrEmpty(id));
    // Copy to get the updated_at field, and restore ID (not copied over)
    account = new JongoUserAccount(account);
    account.setId(id);
    // XXX: don't allow modifying the email address or phone number verified
    account.setEmail_address(null);
    account.setEmail_verified(null);
    account.setPhone_number_verified(null);
    account = getAccountCollection()
        .findAndModify("{ id: #, updated_at: { $in: # } }", id, Longs.asList(versions))
        .with("{ $set: # }", account)
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
  public UserAccount verifyEmailAddress(String id) {
    // XXX: use a JongoUserAccount to update the updated_at field
    JongoUserAccount userAccount = new JongoUserAccount();
    userAccount.setId(id);
    userAccount.setEmail_verified(true);
    return getAccountCollection()
        .findAndModify("{ id: # }", id)
        .with("{ $set: # }", userAccount)
        .returnNew()
        .as(JongoUserAccount.class);
  }

  @Override
  public boolean deleteUserAccount(String id) {
    return getAccountCollection()
        .remove("{ id: # }", id)
        .getN() > 0;
  }

  @Override
  public void bootstrap() {
    getAccountCollection().ensureIndex("{ id : 1 }", "{ unique: 1 }");
    getAccountCollection().ensureIndex("{ email_address : 1 }", "{ unique: 1, sparse: 1 }");
  }
}

