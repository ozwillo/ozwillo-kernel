package oasis.model.accounts;

public interface AccountRepository {
  Account getAccount(String id);

  Account getAccountByToken(Token token);

  UserAccount getUserAccountByEmail(String email);

  UserAccount getUserAccountById(String id);
}
