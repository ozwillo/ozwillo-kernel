package oasis.model.accounts;

public interface AccountRepository {
  Account getAccount(String id);

  Account getAccountByTokenId(String tokenId);

  UserAccount getUserAccountByEmail(String email);

  UserAccount getUserAccountById(String id);

  AgentAccount getAgentAccountById(String id);

  String createAgentAccount(String organizationId, AgentAccount agent);

  boolean deleteAgentAccount(String agentId);

  void deleteAgentAccountsFromOrganization(String organizationId);

  Iterable<AgentAccount> getAgentsForOrganization(String organizationId, int start, int limit);
}
