package oasis.model.accounts;

public interface AccountRepository {
  Account getAccount(String id);

  UserAccount getUserAccountByEmail(String email);

  UserAccount getUserAccountById(String id);

  AgentAccount getAgentAccountById(String id);

 @Deprecated AgentAccount createAgentAccount(String organizationId, AgentAccount agent);

  // FIXME: replace with some updateAccount (or move out of Account)
  void updatePassword(String accountId, String passwordHash, String passwordSalt);
}
