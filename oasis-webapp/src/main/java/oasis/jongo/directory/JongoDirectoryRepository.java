package oasis.jongo.directory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.mongodb.WriteResult;

import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Group;
import oasis.model.directory.Organization;

public class JongoDirectoryRepository implements DirectoryRepository {
  private final static Logger logger = LoggerFactory.getLogger(DirectoryRepository.class);

  @Inject
  private Jongo jongo;

  @Override
  public Organization getOrganization(String organizationId) {
    return getOrganizationCollection()
        .findOne("{ id: # }", organizationId)
        .projection("{ name: 1, id: 1, modified: 1}")
        .as(Organization.class);
  }

  @Override
  public Organization getOrganizationFromGroup(String groupId) {
    return getOrganizationCollection()
        .findOne("{ groups.id: # }", groupId)
        .projection("{ name: 1, id: 1, modified: 1}")
        .as(Organization.class);
  }

  @Override
  public Iterable<Organization> getOrganizations() {
    return getOrganizationCollection().find().projection("{ name: 1, id: 1, modified: 1}")
        .as(Organization.class);
  }

  @Override
  public Organization createOrganization(Organization organization) {
    JongoOrganization jongoOrganization = new JongoOrganization(organization);
    getOrganizationCollection().insert(jongoOrganization);
    return jongoOrganization;
  }

  @Override
  public void updateOrganization(String organizationId, Organization organization) {
    long modified = System.currentTimeMillis();
    List<Object> updateParameters = new ArrayList<>(2);
    StringBuilder updateObject = new StringBuilder("modified:#");
    updateParameters.add(modified);

    if (organization.getName() != null) {
      updateObject.append(",name:#");
      updateParameters.add(organization.getName());
    }

    // TODO : check modified
    WriteResult wr = getOrganizationCollection()
        .update("{ id: # }", organizationId)
        .with("{ $set: {" + updateObject.toString() + " } }", updateParameters.toArray());

    if (wr.getN() != 1) {
      // TODO: more precise message
      logger.warn("The organization {} does not exist", organizationId);
    }
  }

  @Override
  public boolean deleteOrganization(String organizationId) {
    // TODO : check modified
    WriteResult wr = getOrganizationCollection().remove("{ id: # }", organizationId);

    return wr.getN() == 1;
  }

  @Override
  public Group getGroup(String groupId) {
    JongoOrganization organization = getOrganizationCollection()
        .findOne("{ groups.id: # }", groupId)
        .projection("{ id:1, groups.$: 1}")
        .as(JongoOrganization.class);

    if (organization == null) {
      return null;
    }

    if (organization.getGroups() == null || organization.getGroups().isEmpty()) {
      return null;
    }

    return Iterables.getOnlyElement(organization.getGroups());
  }

  @Override
  public Collection<Group> getGroups(String organizationId) {
    JongoOrganization organization = getOrganizationCollection()
        .findOne("{ id: # }", organizationId)
        .projection("{ id: 1, groups: 1 }")
        .as(JongoOrganization.class);

    if (organization == null) {
      return null;
    }

    if (organization.getGroups() == null) {
      return Collections.emptyList();
    }

    return (Collection<Group>) (Collection<?>) organization.getGroups();
  }

  @Override
  public Group createGroup(String organizationId, Group group) {
    JongoGroup jongoGroup = new JongoGroup(group);

    // TODO : check modified
    WriteResult wr = getOrganizationCollection()
        .update("{ id: # }", organizationId)
        .with("{ $push: { groups: # } }", jongoGroup);
    if (wr.getN() != 1) {
      // TODO: more precise message
      logger.warn("The organization {} does not exist", organizationId);
    }
    return jongoGroup;
  }

  @Override
  public void updateGroup(String groupId, Group group) {
    long modified = System.currentTimeMillis();
    List<Object> updateParameters = new ArrayList<>(3);
    StringBuilder updateObject = new StringBuilder("groups.$.modified:#");
    updateParameters.add(modified);

    if (group.getName() != null) {
      updateObject.append(",groups.$.name:#");
      updateParameters.add(group.getName());
    }

    // TODO : check modified
    WriteResult wr = getOrganizationCollection()
        .update("{ groups.id: # }", groupId)
        .with("{ $set: {" + updateObject.toString() + " } }", updateParameters.toArray());

    if (wr.getN() != 1) {
      // TODO: more precise message
      logger.warn("The group {} does not exist", groupId);
    }
  }

  @Override
  public boolean deleteGroup(String groupId) {
    // TODO: check modified
    WriteResult wr = getOrganizationCollection()
        .update("{ groups.id: # }", groupId)
        .with("{ $pull: { groups: { id: # } } }", groupId);

    return wr.getN() == 1;
  }

  @Override
  public Collection<Group> getGroupsForAgent(final String agentId) {
    JongoOrganization organization = getOrganizationCollection()
        .findOne("{ groups.agentIds : # }", agentId)
        .projection("{ id: 1, groups: 1 }")
        .as(JongoOrganization.class);
    if (organization == null) {
      return null;
    }
    if (organization.getGroups() == null) {
      return Collections.emptyList();
    }

    return (Collection<Group>) (Collection<?>) Collections2.filter(organization.getGroups(), new Predicate<JongoGroup>() {
      @Override
      public boolean apply(@Nullable JongoGroup input) {
        return input != null && input.getAgentIds() != null && input.getAgentIds().contains(agentId);
      }
    });
  }

  @Override
  public Collection<String> getGroupMembers(String groupId) {
    JongoGroup group = (JongoGroup) getGroup(groupId);
    if (group == null) {
      return null;
    }
    if (group.getAgentIds() == null) {
      return Collections.emptyList();
    }

    return group.getAgentIds();
  }

  @Override
  public void addGroupMember(String groupId, String agentId) {
    long modified = System.currentTimeMillis();

    // TODO: check modified
    WriteResult wr = getOrganizationCollection()
        .update("{ groups: { $elemMatch: { agentIds: { $ne: # }, id: # } } }", agentId, groupId)
        .with("{ $set: { groups.$.modified: # }, $addToSet: { groups.$.agentIds: # } }", modified, agentId);

    if (wr.getN() != 1) {
      // TODO: more precise message
      logger.warn("The group {} does not exist or the agent {} is already in the group", groupId, agentId);
    }
  }

  @Override
  public boolean removeGroupMember(String groupId, String agentId) {
    long modified = System.currentTimeMillis();

    // TODO: check modified
    WriteResult wr = getOrganizationCollection()
        .update("{ groups: { $elemMatch: { agentIds: #, id: # } } }", agentId, groupId)
        .with("{ $set: { groups.$.modified: # }, $pull: { groups.$.agentIds: # } }", modified, agentId);

    if (wr.getN() != 1) {
      // TODO: more precise message
      logger.warn("The group {} does not exist or the agent {} is not in the group", groupId, agentId);
    }
    return wr.getN() > 0;
  }

  private MongoCollection getOrganizationCollection() {
    return jongo.getCollection("organization");
  }
}
