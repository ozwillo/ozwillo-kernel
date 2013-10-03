package oasis.model.directory;

import oasis.model.accounts.AgentAccount;

import java.util.Collection;

public interface DirectoryRepository {
  public Group getGroup(String id);

  public Collection<AgentAccount> getGroupMembers(String groupId);

  public Organization getOrganization(String id);

  public Collection<AgentAccount> getOrganizationMembers(String organizationId);

  public Collection<Group> getGroups(String organizationId);
}
