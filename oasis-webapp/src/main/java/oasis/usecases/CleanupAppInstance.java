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
