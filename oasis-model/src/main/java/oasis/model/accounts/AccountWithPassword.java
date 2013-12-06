package oasis.model.accounts;

public interface AccountWithPassword {
  String getPassword();

  void setPassword(String password);

  String getPasswordSalt();

  void setPasswordSalt(String passwordSalt);
}
