package oasis.model.accounts;

import oasis.model.InvalidVersionException;

public interface AccountRepository {
  Account getAccount(String id);

  UserAccount getUserAccountByEmail(String email);

  UserAccount getUserAccountById(String id);

  AgentAccount getAgentAccountById(String id);

  AgentAccount createAgentAccount(String organizationId, AgentAccount agent);

  boolean deleteAgentAccount(String agentId, long[] versions) throws InvalidVersionException;

  void deleteAgentAccountsFromOrganization(String organizationId);

  Iterable<AgentAccount> getAgentsForOrganization(String organizationId, int start, int limit);

  AgentAccount findAndRemove(String agentId, long[] versions) throws InvalidVersionException;

  // FIXME: replace with some updateAccount (or move out of Account)
  void updatePassword(String accountId, String passwordHash, String passwordSalt);
}
