/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.jongo.directory;

import static com.google.common.base.Preconditions.*;

import java.time.Instant;
import java.util.Date;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.primitives.Longs;
import com.mongodb.DuplicateKeyException;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoCommandException;
import com.mongodb.WriteResult;

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
    getOrganizationMembershipsCollection().ensureIndex("{ organizationId: 1, email: 1, accountId: 1 }", "{ unique: 1 }");
  }

  @Override
  public OrganizationMembership createOrganizationMembership(OrganizationMembership membership) {
    checkArgument(!Strings.isNullOrEmpty(membership.getAccountId()));
    checkArgument(!Strings.isNullOrEmpty(membership.getOrganizationId()));
    checkArgument(membership.getStatus() == OrganizationMembership.Status.ACCEPTED);
    checkArgument(Strings.isNullOrEmpty(membership.getEmail()));

    JongoOrganizationMembership member = new JongoOrganizationMembership(membership);
    member.setCreated(Instant.now());
    try {
      getOrganizationMembershipsCollection().insert(member);
    } catch (DuplicateKeyException e) {
      return null;
    }
    return member;
  }

  @Override
  public OrganizationMembership createPendingOrganizationMembership(OrganizationMembership membership) {
    checkArgument(!Strings.isNullOrEmpty(membership.getEmail()));
    checkArgument(!Strings.isNullOrEmpty(membership.getOrganizationId()));
    checkArgument(membership.getStatus() == OrganizationMembership.Status.PENDING);
    checkArgument(Strings.isNullOrEmpty(membership.getAccountId()));

    JongoOrganizationMembership member = new JongoOrganizationMembership(membership);
    member.setCreated(Instant.now());
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
        .findOne("{ id: #, $or: [ { status: { $exists: 0 } }, { status: # } ] }", id, OrganizationMembership.Status.ACCEPTED)
        .as(JongoOrganizationMembership.class);
  }

  @Nullable
  @Override
  public OrganizationMembership getPendingOrganizationMembership(String id) {
    return getOrganizationMembershipsCollection()
        .findOne("{ id: #, status: # }", id, OrganizationMembership.Status.PENDING)
        .as(JongoOrganizationMembership.class);
  }

  @Nullable
  @Override
  public OrganizationMembership getOrganizationMembership(String userId, String organizationId) {
    return getOrganizationMembershipsCollection()
        .findOne("{ accountId: #, organizationId: #, $or: [ { status: { $exists: 0 } }, { status: # } ] }",
            userId, organizationId, OrganizationMembership.Status.ACCEPTED)
        .as(JongoOrganizationMembership.class);
  }

  @Nullable
  @Override
  public OrganizationMembership updateOrganizationMembership(OrganizationMembership membership, long[] versions) throws InvalidVersionException {
    String membershipId = membership.getId();
    checkArgument(!Strings.isNullOrEmpty(membershipId));
    checkArgument(!Strings.isNullOrEmpty(membership.getAccountId()));
    checkArgument(!Strings.isNullOrEmpty(membership.getOrganizationId()));
    checkArgument(membership.getStatus() == OrganizationMembership.Status.ACCEPTED);

    // Copy to get the modified field, then reset ID (not copied over) to make sure we won't generate a new one
    membership = new JongoOrganizationMembership(membership);
    membership.setId(membershipId);

    JongoOrganizationMembership res = getOrganizationMembershipsCollection()
        .findAndModify("{ id: #, modified: { $in: # }, $or: [ { status: { $exists: 0 } }, { status: # } ] }",
            membershipId, Longs.asList(versions), OrganizationMembership.Status.ACCEPTED)
        .returnNew()
        .with("{ $set: # }", membership)
        .as(JongoOrganizationMembership.class);

    if (res == null) {
      if (getOrganizationMembershipsCollection().count("{ id: #, $or: [ { status: { $exists: 0 } }, { status: # } ] }",
          membershipId, OrganizationMembership.Status.ACCEPTED) != 0) {
        throw new InvalidVersionException("organizationMember", membershipId);
      }
      logger.warn("Organization member {} does not exist", membershipId);
    }

    return res;
  }

  @Nullable
  @Override
  public OrganizationMembership acceptPendingOrganizationMembership(String membershipId, String accountId) {
    checkArgument(!Strings.isNullOrEmpty(membershipId));
    checkArgument(!Strings.isNullOrEmpty(accountId));

    try {
      return getOrganizationMembershipsCollection()
          .findAndModify("{ id: #, status: # }", membershipId, OrganizationMembership.Status.PENDING)
          .returnNew()
          .with("{ $set: { status: #, accountId: #, accepted: # }, $unset: { email: '' } }",
              OrganizationMembership.Status.ACCEPTED, accountId, new Date())
          .as(JongoOrganizationMembership.class);
    } catch (MongoCommandException e) {
      if (ErrorCategory.fromErrorCode(e.getErrorCode()) == ErrorCategory.DUPLICATE_KEY) {
        throw new oasis.model.DuplicateKeyException();
      }
      throw e;
    } catch (DuplicateKeyException e) {
      throw new oasis.model.DuplicateKeyException();
    }
  }

  @Override
  public boolean deleteOrganizationMembership(String id, long[] versions) throws InvalidVersionException {
    int n = getOrganizationMembershipsCollection()
        .remove("{id: #, modified: { $in: # }, $or: [ { status: { $exists: 0 } }, { status: # } ] }",
            id, Longs.asList(versions), OrganizationMembership.Status.ACCEPTED)
        .getN();
    if (n == 0) {
      if (getOrganizationMembershipsCollection().count("{ id: #, $or: [ { status: { $exists: 0 } }, { status: # } ] }",
          id, OrganizationMembership.Status.ACCEPTED) != 0) {
        throw new InvalidVersionException("organization", id);
      }
      return false;
    }

    return true;
  }

  @Override
  public boolean deletePendingOrganizationMembership(String id) {
    WriteResult writeResult = getOrganizationMembershipsCollection()
        .remove("{id: #, status: # }", id, OrganizationMembership.Status.PENDING);
    return writeResult.getN() > 0;
  }

  @Override
  public boolean deletePendingOrganizationMembership(String id, long[] versions) throws InvalidVersionException {
    int n = getOrganizationMembershipsCollection()
        .remove("{id: #, modified: { $in: # }, status: # }", id, Longs.asList(versions), OrganizationMembership.Status.PENDING)
        .getN();
    if (n == 0) {
      if (getOrganizationMembershipsCollection().count("{ id: #, status: # }", id, OrganizationMembership.Status.PENDING) != 0) {
        throw new InvalidVersionException("organization", id);
      }
      return false;
    }

    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<OrganizationMembership> getPendingMembersOfOrganization(String organizationId, int start, int limit) {
    return (Iterable<OrganizationMembership>) (Iterable<?>) getOrganizationMembershipsCollection()
        .find("{ organizationId: #, status: # }", organizationId, OrganizationMembership.Status.PENDING)
        .skip(start)
        .limit(limit)
        .as(JongoOrganizationMembership.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<OrganizationMembership> getMembersOfOrganization(String organizationId, int start, int limit) {
    return (Iterable<OrganizationMembership>) (Iterable<?>) getOrganizationMembershipsCollection()
        .find("{ organizationId: #, $or: [ { status: { $exists: 0 } }, { status: # } ] }", organizationId, OrganizationMembership.Status.ACCEPTED)
        .skip(start)
        .limit(limit)
        .as(JongoOrganizationMembership.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<OrganizationMembership> getAdminsOfOrganization(String organizationId) {
    return (Iterable<OrganizationMembership>) (Iterable<?>) getOrganizationMembershipsCollection()
        .find("{ organizationId: #, admin: true, $or: [ { status: { $exists: 0 } }, { status: # } ] }", organizationId, OrganizationMembership.Status.ACCEPTED)
        .as(JongoOrganizationMembership.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<OrganizationMembership> getAdminsOfOrganization(String organizationId, int start, int limit) {
    return (Iterable<OrganizationMembership>) (Iterable<?>) getOrganizationMembershipsCollection()
        .find("{ organizationId: #, admin: true, $or: [ { status: { $exists: 0 } }, { status: # } ] }", organizationId, OrganizationMembership.Status.ACCEPTED)
        .skip(start)
        .limit(limit)
        .as(JongoOrganizationMembership.class);
  }

  @Override
  public Iterable<String> getOrganizationIdsForUser(String userId) {
    return getOrganizationMembershipsCollection().distinct("organizationId")
        .query("{ accountId: #, $or: [ { status: { $exists: 0 } }, { status: # } ] }", userId, OrganizationMembership.Status.ACCEPTED)
        .as(String.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<OrganizationMembership> getOrganizationsForUser(String userId, int start, int limit) {
    return (Iterable<OrganizationMembership>) (Iterable<?>) getOrganizationMembershipsCollection()
        .find("{ accountId: #, $or: [ { status: { $exists: 0 } }, { status: # } ] }", userId, OrganizationMembership.Status.ACCEPTED)
        .skip(start)
        .limit(limit)
        .as(JongoOrganizationMembership.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<OrganizationMembership> getOrganizationsForAdmin(String userId) {
    return (Iterable<OrganizationMembership>) (Iterable<?>) getOrganizationMembershipsCollection()
        .find("{ accountId: #, admin: true, $or: [ { status: { $exists: 0 } }, { status: # } ] }", userId, OrganizationMembership.Status.ACCEPTED)
        .as(JongoOrganizationMembership.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<OrganizationMembership> getOrganizationsForAdmin(String userId, int start, int limit) {
    return (Iterable<OrganizationMembership>) (Iterable<?>) getOrganizationMembershipsCollection()
        .find("{ accountId: #, admin: true, $or: [ { status: { $exists: 0 } }, { status: # } ] }", userId, OrganizationMembership.Status.ACCEPTED)
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
