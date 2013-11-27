package oasis.services.accounts;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import oasis.model.accounts.Account;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.Token;
import oasis.model.accounts.UserAccount;

public class JongoAccountRepository implements AccountRepository {
  @Inject
  protected Jongo jongo;

  protected MongoCollection getAccountCollection() {
    return jongo.getCollection("account");
  }

  @Override
  public Account getAccount(String id) {
    return this.getAccountCollection().findOne("{id:#}", id).as(Account.class);
  }

  @Override
  public Account getAccountByToken(Token token) {
    return this.getAccountCollection().findOne("{tokens.id:#}", token.getId()).as(Account.class);
  }

  @Override
  public UserAccount getUserAccountByEmail(String email) {
    return this.getAccountCollection().findOne("{emailAddress:#}", email).as(UserAccount.class);
  }

  @Override
  public UserAccount getUserAccountById(String id) {
    return this.getAccountCollection().findOne("{id:#}", id).as(UserAccount.class);
  }
}
