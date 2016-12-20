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
package oasis.model.directory;

import javax.annotation.Nullable;

import oasis.model.InvalidVersionException;

public interface OrganizationMembershipRepository {
  @Nullable OrganizationMembership createOrganizationMembership(OrganizationMembership membership);

  @Nullable OrganizationMembership createPendingOrganizationMembership(OrganizationMembership membership);

  @Nullable OrganizationMembership getOrganizationMembership(String id);

  @Nullable OrganizationMembership getPendingOrganizationMembership(String id);

  @Nullable OrganizationMembership getOrganizationMembership(String userId, String organizationId);

  @Nullable OrganizationMembership updateOrganizationMembership(OrganizationMembership membership, long[] versions) throws InvalidVersionException;

  @Nullable OrganizationMembership acceptPendingOrganizationMembership(String membershipId, String accountId);

  boolean deleteOrganizationMembership(String id, long[] versions) throws InvalidVersionException;

  boolean deletePendingOrganizationMembership(String id);

  boolean deletePendingOrganizationMembership(String id, long[] versions) throws InvalidVersionException;

  Iterable<OrganizationMembership> getPendingMembersOfOrganization(String organizationId, int start, int limit);

  Iterable<OrganizationMembership> getMembersOfOrganization(String organizationId, int start, int limit);

  Iterable<OrganizationMembership> getAdminsOfOrganization(String organizationId);

  Iterable<OrganizationMembership> getAdminsOfOrganization(String organizationId, int start, int limit);

  Iterable<String> getOrganizationIdsForUser(String userId);

  Iterable<OrganizationMembership> getOrganizationsForUser(String userId, int start, int limit);

  Iterable<OrganizationMembership> getOrganizationsForAdmin(String userId);

  Iterable<OrganizationMembership> getOrganizationsForAdmin(String userId, int start, int limit);

  boolean deleteMembershipsInOrganization(String organizationId);
}
