package oasis.jongo.directory;

import static com.google.common.base.Preconditions.*;

import java.util.NoSuchElementException;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

import oasis.jongo.JongoBootstrapper;
import oasis.model.InvalidVersionException;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;

public class JongoOrganizationMembershipRepository implements OrganizationMembershipRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(OrganizationMembershipRepository.class);

  private final Jongo jongo;

  @Inject JongoOrganizationMembershipRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  private MongoCollection getOrganizationMembershipsCollection() {
    return jongo.getCollection("organization_memberships");
  }

  @Override
  public void bootstrap() {
    getOrganizationMembershipsCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    getOrganizationMembershipsCollection().ensureIndex("{ accountId: 1, organizationId: 1 }", "{ unique: 1 }");
  }

  @Override
  public OrganizationMembership createOrganizationMembership(OrganizationMembership membership) {
    checkArgument(!Strings.isNullOrEmpty(membership.getAccountId()));
    checkArgument(!Strings.isNullOrEmpty(membership.getOrganizationId()));

    JongoOrganizationMembership member = new JongoOrganizationMembership(membership);
    getOrganizationMembershipsCollection().insert(member);
    return member;
  }

  @Nullable
  @Override
  public OrganizationMembership getOrganizationMembership(String id) {
    return getOrganizationMembershipsCollection()
        .findOne("{ id: # }", id)
        .as(JongoOrganizationMembership.class);
  }

  @Nullable
  @Override
  public OrganizationMembership getOrganizationMembership(String userId, String organizationId) {
    return getOrganizationMembershipsCollection()
        .findOne("{ accountId: #, organizationId: # }", userId, organizationId)
        .as(JongoOrganizationMembership.class);
  }

  @Nullable
  @Override
  public OrganizationMembership updateOrganizationMembership(OrganizationMembership membership, long[] versions) throws InvalidVersionException {
    checkArgument(!Strings.isNullOrEmpty(membership.getId()));
    checkArgument(!Strings.isNullOrEmpty(membership.getAccountId()));
    checkArgument(!Strings.isNullOrEmpty(membership.getOrganizationId()));

    JongoOrganizationMembership res = getOrganizationMembershipsCollection()
        .findAndModify("{ id: #, modified: { $in: # } }", membership.getId(), versions)
        .returnNew()
        .with("{ $set: # }", new JongoOrganizationMembership(membership))
        .as(JongoOrganizationMembership.class);

    if (res == null) {
      if (getOrganizationMembershipsCollection().count("{ id, # }", membership.getId()) != 0) {
        throw new InvalidVersionException("organizationMember", membership.getId());
      }
      logger.warn("Organization member {} does not exist", membership.getId());
    }

    return res;
  }

  @Override
  public boolean deleteOrganizationMembership(String id, long[] versions) throws InvalidVersionException {
    int n = getOrganizationMembershipsCollection().remove("{id: #, modified: { $in: # } }", id, versions).getN();
    if (n == 0) {
      if (getOrganizationMembershipsCollection().count("{ id: # }", id) != 0) {
        throw new InvalidVersionException("organization", id);
      }
      return false;
    }

    return true;
  }

  @Override
  public Iterable<OrganizationMembership> getMembersOfOrganization(String organizationId, int start, int limit) {
    return getOrganizationMembershipsCollection()
        .find("{ organizationId; # }", organizationId)
        .skip(start)
        .limit(limit)
        .as(OrganizationMembership.class);
  }

  @Override
  public Iterable<OrganizationMembership> getOrganizationsForUser(String userId, int start, int limit) {
    return getOrganizationMembershipsCollection()
        .find("{ accountId; # }", userId)
        .skip(start)
        .limit(limit)
        .as(OrganizationMembership.class);
  }

  @Nullable
  @Override
  public OrganizationMembership getOrganizationForUserIfUnique(String userId) {
    try {
      return Iterables.getOnlyElement(getOrganizationsForUser(userId, 0, 2));
    } catch (NoSuchElementException | IllegalArgumentException e) {
      return null;
    }
  }

  @Override
  public boolean deleteMembershipsInOrganization(String organizationId) {
    return getOrganizationMembershipsCollection()
        .remove("{ organizationId: # }", organizationId)
        .getN() > 0;
  }
}
