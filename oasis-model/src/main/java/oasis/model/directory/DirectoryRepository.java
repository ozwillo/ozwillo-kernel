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
package oasis.model.directory;

import org.joda.time.Instant;

import oasis.model.InvalidVersionException;

public interface DirectoryRepository {
  Organization getOrganization(String organizationId);

  Organization createOrganization(Organization organization);

  Organization updateOrganization(String organizationId, Organization organization, long[] versions) throws InvalidVersionException;

  boolean deleteOrganization(String organizationId);

  boolean deleteOrganization(String organizationId, Organization.Status status);

  Organization changeOrganizationStatus(String organizationId, Organization.Status newStatus, String requesterId);

  Organization changeOrganizationStatus(String organizationId, Organization.Status newStatus, String requesterId, long[] versions)
      throws InvalidVersionException;

  Iterable<Organization> getOrganizations();

  Iterable<Organization> findOrganizationsDeletedBefore(Instant deletedBefore);
}
