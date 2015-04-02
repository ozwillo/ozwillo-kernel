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

  Iterable<OrganizationMembership> getMembersOfOrganization(String organizationId, int start, int limit);

  Iterable<OrganizationMembership> getAdminsOfOrganization(String organizationId);

  Iterable<OrganizationMembership> getAdminsOfOrganization(String organizationId, int start, int limit);

  Iterable<OrganizationMembership> getOrganizationsForUser(String userId, int start, int limit);

  Iterable<OrganizationMembership> getOrganizationsForAdmin(String userId);

  Iterable<OrganizationMembership> getOrganizationsForAdmin(String userId, int start, int limit);

  boolean deleteMembershipsInOrganization(String organizationId);
}
