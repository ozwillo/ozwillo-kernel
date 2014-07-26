package oasis.model.accounts;

public interface AccountRepository {
  UserAccount getUserAccountByEmail(String email);

  UserAccount getUserAccountById(String id);

  UserAccount createUserAccount(UserAccount agent);
}
