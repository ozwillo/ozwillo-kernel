package oasis.model.directory;

import java.util.Collection;

import oasis.model.accounts.AgentAccount;

public interface DirectoryRepository {
  Group getGroup(String id);

  Collection<AgentAccount> getGroupMembers(String groupId);

  Organization getOrganization(String id);

  Collection<AgentAccount> getOrganizationMembers(String organizationId);

  Collection<Group> getGroups(String organizationId);
}
