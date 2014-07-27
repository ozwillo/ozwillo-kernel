package oasis.model.accounts;

import oasis.model.InvalidVersionException;

public interface AccountRepository {
  UserAccount getUserAccountByEmail(String email);

  UserAccount getUserAccountById(String id);

  UserAccount createUserAccount(UserAccount agent);

  UserAccount updateAccount(UserAccount account, long[] versions) throws InvalidVersionException;
}
