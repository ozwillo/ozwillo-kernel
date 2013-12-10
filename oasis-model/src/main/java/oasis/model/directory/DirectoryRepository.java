package oasis.model.directory;

import java.util.Collection;

public interface DirectoryRepository {
  Organization getOrganization(String organizationId);

  Organization getOrganizationFromGroup(String groupId);

  Collection<Group> getGroups(String organizationId);

  Organization createOrganization(Organization organization);

  Organization updateOrganization(String organizationId, Organization organization);

  boolean deleteOrganization(String organizationId);

  Iterable<Organization> getOrganizations();

  Group getGroup(String groupId);

  Collection<String> getGroupMembers(String groupId);

  void addGroupMember(String groupId, String agentId);

  boolean removeGroupMember(String groupId, String agentId);

  Group createGroup(String organizationId, Group group);

  Group updateGroup(String groupId, Group group);

  boolean deleteGroup(String groupId);

  Collection<Group> getGroupsForAgent(String agentId);
}
