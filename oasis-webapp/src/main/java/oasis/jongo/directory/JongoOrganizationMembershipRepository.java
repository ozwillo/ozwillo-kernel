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
import com.google.common.primitives.Longs;
import com.mongodb.DuplicateKeyException;

import oasis.jongo.JongoBootstrapper;
import oasis.jongo.applications.v2.JongoService;
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
    member.initCreated();
    try {
      getOrganizationMembershipsCollection().insert(member);
    } catch (DuplicateKeyException e) {
      return null;
    }
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
    String membershipId = membership.getId();
    checkArgument(!Strings.isNullOrEmpty(membershipId));
    checkArgument(!Strings.isNullOrEmpty(membership.getAccountId()));
    checkArgument(!Strings.isNullOrEmpty(membership.getOrganizationId()));

    // Copy to get the modified field, then reset ID (not copied over) to make sure we won't generate a new one
    membership = new JongoOrganizationMembership(membership);
    membership.setId(membershipId);

    JongoOrganizationMembership res = getOrganizationMembershipsCollection()
        .findAndModify("{ id: #, modified: { $in: # } }", membershipId, Longs.asList(versions))
        .returnNew()
        .with("{ $set: # }", membership)
        .as(JongoOrganizationMembership.class);

    if (res == null) {
      if (getOrganizationMembershipsCollection().count("{ id: # }", membershipId) != 0) {
        throw new InvalidVersionException("organizationMember", membershipId);
      }
      logger.warn("Organization member {} does not exist", membershipId);
    }

    return res;
  }

  @Override
  public boolean deleteOrganizationMembership(String id, long[] versions) throws InvalidVersionException {
    int n = getOrganizationMembershipsCollection().remove("{id: #, modified: { $in: # } }", id, Longs.asList(versions)).getN();
    if (n == 0) {
      if (getOrganizationMembershipsCollection().count("{ id: # }", id) != 0) {
        throw new InvalidVersionException("organization", id);
      }
      return false;
    }

    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<OrganizationMembership> getMembersOfOrganization(String organizationId, int start, int limit) {
    return (Iterable<OrganizationMembership>) (Iterable<?>) getOrganizationMembershipsCollection()
        .find("{ organizationId: # }", organizationId)
        .skip(start)
        .limit(limit)
        .as(JongoOrganizationMembership.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<OrganizationMembership> getAdminsOfOrganization(String organizationId) {
    return (Iterable<OrganizationMembership>) (Iterable<?>) getOrganizationMembershipsCollection()
        .find("{ organizationId: #, admin: true }", organizationId)
        .as(JongoOrganizationMembership.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<OrganizationMembership> getAdminsOfOrganization(String organizationId, int start, int limit) {
    return (Iterable<OrganizationMembership>) (Iterable<?>) getOrganizationMembershipsCollection()
        .find("{ organizationId: #, admin: true }", organizationId)
        .skip(start)
        .limit(limit)
        .as(JongoOrganizationMembership.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<OrganizationMembership> getOrganizationsForUser(String userId, int start, int limit) {
    return (Iterable<OrganizationMembership>) (Iterable<?>) getOrganizationMembershipsCollection()
        .find("{ accountId: # }", userId)
        .skip(start)
        .limit(limit)
        .as(JongoOrganizationMembership.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<OrganizationMembership> getOrganizationsForAdmin(String userId) {
    return (Iterable<OrganizationMembership>) (Iterable<?>) getOrganizationMembershipsCollection()
        .find("{ accountId: #, admin: true }", userId)
        .as(JongoOrganizationMembership.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<OrganizationMembership> getOrganizationsForAdmin(String userId, int start, int limit) {
    return (Iterable<OrganizationMembership>) (Iterable<?>) getOrganizationMembershipsCollection()
        .find("{ accountId: #, admin: true }", userId)
        .skip(start)
        .limit(limit)
        .as(JongoOrganizationMembership.class);
  }

  @Override
  public boolean deleteMembershipsInOrganization(String organizationId) {
    return getOrganizationMembershipsCollection()
        .remove("{ organizationId: # }", organizationId)
        .getN() > 0;
  }
}
