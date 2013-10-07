package oasis.services.directory;

import oasis.model.accounts.AgentAccount;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Group;
import oasis.model.directory.Organization;

import java.util.Collection;

public class DummyDirectoryRepository implements DirectoryRepository{
  @Override
  public Group getGroup(String id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<AgentAccount> getGroupMembers(String groupId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Organization getOrganization(String id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<AgentAccount> getOrganizationMembers(String organizationId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<Group> getGroups(String organizationId) {
    throw new UnsupportedOperationException();
  }
}
