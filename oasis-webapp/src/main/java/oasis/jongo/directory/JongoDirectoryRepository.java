/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
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

import java.util.Map;

import javax.inject.Inject;

import org.joda.time.Instant;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.common.primitives.Longs;
import com.mongodb.DuplicateKeyException;
import com.mongodb.WriteResult;

import oasis.jongo.JongoBootstrapper;
import oasis.model.InvalidVersionException;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;

public class JongoDirectoryRepository implements DirectoryRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(DirectoryRepository.class);

  private final Jongo jongo;

  @Inject
  JongoDirectoryRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  @Override
  public Organization getOrganization(String organizationId) {
    return getOrganizationCollection()
        .findOne("{ id: # }", organizationId)
        .as(JongoOrganization.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<Organization> getOrganizations() {
    return (Iterable<Organization>) (Iterable<?>) getOrganizationCollection().find()
        .as(JongoOrganization.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<Organization> findOrganizationsDeletedBefore(Instant deletedBefore) {
    return (Iterable<Organization>) (Iterable<?>) getOrganizationCollection()
        .find("{ status: #, status_changed: { $lt: # } }", Organization.Status.DELETED, deletedBefore.toDate())
        .as(JongoOrganization.class);
  }

  @Override
  public Organization createOrganization(Organization organization) {
    JongoOrganization jongoOrganization = new JongoOrganization(organization);
    jongoOrganization.initCreated();
    try {
      getOrganizationCollection().insert(jongoOrganization);
    } catch (DuplicateKeyException e) {
      return null;
    }
    return jongoOrganization;
  }

  @Override
  public Organization updateOrganization(String organizationId, Organization organization, long[] versions) throws InvalidVersionException {
    organization = new JongoOrganization(organization);
    // Don't allow updating the status
    organization.setStatus(null);
    organization.setStatus_changed(null);
    organization.setStatus_change_requester_id(null);

    Map<String, Boolean> unsetObject = Maps.newLinkedHashMap();
    if (organization.getTerritory_id() == null) {
      unsetObject.put("territory_id", true);
    }

    JongoOrganization res = getOrganizationCollection()
        .findAndModify("{ id: #, modified: { $in: # } }", organizationId, Longs.asList(versions))
        .returnNew()
        .with("{ $set: #, $unset: # }", organization, unsetObject)
        .as(JongoOrganization.class);

    if (res == null) {
      if (getOrganizationCollection().count("{ id: # }", organizationId) != 0) {
        throw new InvalidVersionException("organization", organizationId);
      }
      logger.warn("The organization {} does not exist", organizationId);
    }

    return res;
  }

  @Override
  public boolean deleteOrganization(String organizationId) {
    WriteResult wr = getOrganizationCollection().remove("{ id: # }", organizationId);
    return wr.getN() > 0;
  }

  @Override
  public boolean deleteOrganization(String organizationId, Organization.Status status) {
    WriteResult wr = getOrganizationCollection().remove("{ id: #, status: # }", organizationId, status);
    return wr.getN() > 0;
  }

  @Override
  public Organization changeOrganizationStatus(String organizationId, Organization.Status newStatus, String requesterId) {
    Instant now = Instant.now();
    return getOrganizationCollection()
        .findAndModify("{ id: # }", organizationId)
        .returnNew()
        .with("{ $set: { status: #, status_changed: #, status_change_requester_id: #, modified: # } }", newStatus, now.toDate(), requesterId, now.getMillis())
        .as(JongoOrganization.class);
  }

  @Override
  public Organization changeOrganizationStatus(String organizationId, Organization.Status newStatus, String requesterId, long[] versions) throws InvalidVersionException {
    Instant now = Instant.now();
    JongoOrganization organization = getOrganizationCollection()
        .findAndModify("{id: #, modified: { $in: # } }", organizationId, Longs.asList(versions))
        .returnNew()
        .with("{ $set: { status: #, status_changed: #, status_change_requester_id: #, modified: # } }", newStatus, now.toDate(), requesterId, now.getMillis())
        .as(JongoOrganization.class);
    if (organization == null) {
      if (getOrganizationCollection().count("{ id: # }", organizationId) != 0) {
        throw new InvalidVersionException("organization", organizationId);
      }
      logger.warn("The organization {} does not exist", organizationId);
    }

    return organization;
  }

  private MongoCollection getOrganizationCollection() {
    return jongo.getCollection("organization");
  }

  @Override
  public void bootstrap() {
    getOrganizationCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
  }
}
