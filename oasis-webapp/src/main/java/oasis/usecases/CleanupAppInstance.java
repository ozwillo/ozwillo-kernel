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
package oasis.usecases;

import java.util.ArrayList;

import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;

import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.Scope;
import oasis.model.applications.v2.ScopeRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.applications.v2.UserSubscriptionRepository;
import oasis.model.authn.TokenRepository;
import oasis.model.authz.AuthorizationRepository;
import oasis.model.eventbus.SubscriptionRepository;

public class CleanupAppInstance {
  @Inject TokenRepository tokenRepository;
  @Inject AuthorizationRepository authorizationRepository;
  @Inject ScopeRepository scopeRepository;
  @Inject AccessControlRepository accessControlRepository;
  @Inject ServiceRepository serviceRepository;
  @Inject UserSubscriptionRepository userSubscriptionRepository;
  @Inject SubscriptionRepository subscriptionRepository;

  public void cleanupInstance(String instance_id, Stats stats) {
    stats.tokensRevokedForInstance = tokenRepository.revokeTokensForClient(instance_id);
    stats.authorizationsDeletedForInstance = authorizationRepository.revokeAllForClient(instance_id);

    ArrayList<String> scopes = new ArrayList<>();
    for (Scope scope : scopeRepository.getScopesOfAppInstance(instance_id)) {
      scopes.add(scope.getId());
    }
    stats.tokensRevokedForScopes = tokenRepository.revokeTokensForScopes(scopes);
    stats.authorizationsDeletedForScopes = authorizationRepository.revokeForAllUsers(scopes);
    stats.scopesDeleted = scopeRepository.deleteScopesOfAppInstance(instance_id);

    stats.appUsersDeleted = accessControlRepository.deleteAccessControlListForAppInstance(instance_id);

    ArrayList<String> serviceIds = new ArrayList<>();
    for (Service service : serviceRepository.getServicesOfInstance(instance_id)) {
      serviceIds.add(service.getId());
    }
    stats.subscriptionsDeleted = userSubscriptionRepository.deleteSubscriptionsForServices(serviceIds);
    stats.servicesDeleted = serviceRepository.deleteServicesOfInstance(instance_id);

    stats.eventBusHooksDeleted = subscriptionRepository.deleteSubscriptionsForAppInstance(instance_id);
  }

  @NotThreadSafe
  public static class Stats {
    public int tokensRevokedForInstance;
    public int authorizationsDeletedForInstance;
    public int tokensRevokedForScopes;
    public int authorizationsDeletedForScopes;
    public int scopesDeleted;
    public int appUsersDeleted;
    public int subscriptionsDeleted;
    public int servicesDeleted;
    public int eventBusHooksDeleted;

    public boolean isEmpty() {
      return tokensRevokedForInstance == 0
          && authorizationsDeletedForInstance == 0
          && tokensRevokedForScopes == 0
          && authorizationsDeletedForScopes == 0
          && scopesDeleted == 0
          && appUsersDeleted == 0
          && subscriptionsDeleted == 0
          && servicesDeleted == 0
          && eventBusHooksDeleted == 0;
    }
  }
}
