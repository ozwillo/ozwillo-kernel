package oasis.model.accounts;

public interface AccountRepository {
  Account getAccount(String id);

  UserAccount getUserAccountByEmail(String email);

  UserAccount getUserAccountById(String id);

  UserAccount createUserAccount(UserAccount agent);
}
