package oasis.model.accounts;

public interface AccountWithPassword {
  public String getPassword();

  public void setPassword(String password);

  public String getPasswordSalt();

  public void setPasswordSalt(String passwordSalt);
}
