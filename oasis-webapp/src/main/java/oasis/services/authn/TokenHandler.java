package oasis.services.authn;

import static com.google.common.base.Preconditions.*;

import java.util.Set;

import javax.inject.Inject;

import org.joda.time.Duration;

import com.google.api.client.util.Clock;
import com.google.common.base.Strings;

import oasis.model.authn.AccessToken;
import oasis.model.authn.AccessTokenGenerator;
import oasis.model.authn.AuthorizationCode;
import oasis.model.authn.RefreshToken;
import oasis.model.authn.SidToken;
import oasis.model.authn.Token;
import oasis.model.authn.TokenRepository;
import oasis.openidconnect.OpenIdConnectModule;

public class TokenHandler {
  private final TokenRepository tokenRepository;
  private final OpenIdConnectModule.Settings settings;
  private final Clock clock;

  @Inject TokenHandler(TokenRepository tokenRepository, OpenIdConnectModule.Settings settings, Clock clock) {
    this.tokenRepository = tokenRepository;
    this.settings = settings;
    this.clock = clock;
  }

  private boolean checkTokenValidity(Token token) {
    // A null token is not valid !
    if (token == null) {
      return false;
    }

    if (token.getExpirationTime().isBefore(clock.currentTimeMillis())) {
      // Token is expired
      return false;
    }

    // The token is valid
    return true;
  }

  public AccessToken createAccessToken(AccessTokenGenerator oauthToken) {
    return this.createAccessToken(settings.accessTokenDuration, oauthToken, null);
  }

  public AccessToken createAccessToken(AccessTokenGenerator oauthToken, Set<String> scopeIds) {
    return this.createAccessToken(settings.accessTokenDuration, oauthToken, scopeIds);
  }

  private AccessToken createAccessToken(Duration ttl, AccessTokenGenerator token, Set<String> scopeIds) {
    if (scopeIds == null || scopeIds.isEmpty()) {
      scopeIds = token.getScopeIds();
    }

    AccessToken newAccessToken = new AccessToken();
    newAccessToken.setAccountId(token.getAccountId());
    newAccessToken.expiresIn(ttl);
    newAccessToken.setScopeIds(scopeIds);

    if (token instanceof AuthorizationCode) {
      if (!tokenRepository.revokeToken(token.getId())) {
        return null;
      }
    } else if (token instanceof RefreshToken) {
      newAccessToken.setParent(token);
    }
    newAccessToken.setServiceProviderId(token.getServiceProviderId());

    if (!registerToken(token.getAccountId(), newAccessToken)) {
      return null;
    }

    // Return the new access token
    return newAccessToken;
  }

  public AuthorizationCode createAuthorizationCode(String accountId, Set<String> scopeIds, String serviceProviderId, String nonce, String redirectUri) {
    checkArgument(!Strings.isNullOrEmpty(accountId));

    AuthorizationCode newAuthorizationCode = new AuthorizationCode();
    newAuthorizationCode.setAccountId(accountId);
    // A AuthorizationCode is available only for 1 minute
    newAuthorizationCode.expiresIn(Duration.standardMinutes(1));
    newAuthorizationCode.setScopeIds(scopeIds);
    newAuthorizationCode.setServiceProviderId(serviceProviderId);
    newAuthorizationCode.setNonce(nonce);
    newAuthorizationCode.setRedirectUri(redirectUri);

    // Register the new token
    if (!registerToken(accountId, newAuthorizationCode)) {
      return null;
    }

    // Return the new token
    return newAuthorizationCode;
  }

  public RefreshToken createRefreshToken(AuthorizationCode authorizationCode) {
    if (!tokenRepository.revokeToken(authorizationCode.getId())) {
      return null;
    }

    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setAccountId(authorizationCode.getAccountId());
    // A RefreshToken is valid 50 years
    refreshToken.expiresIn(Duration.standardDays(50 * 365));
    refreshToken.setScopeIds(authorizationCode.getScopeIds());
    refreshToken.setServiceProviderId(authorizationCode.getServiceProviderId());

    // Register the new token
    if (!registerToken(authorizationCode.getAccountId(), refreshToken)) {
      return null;
    }

    return refreshToken;
  }

  public SidToken createSidToken(String accountId) {
    checkArgument(!Strings.isNullOrEmpty(accountId));

    SidToken newSidToken = new SidToken();
    newSidToken.setAccountId(accountId);
    newSidToken.expiresIn(settings.sidTokenDuration);

    if (!registerToken(accountId, newSidToken)) {
      return null;
    }

    return newSidToken;
  }

  public boolean registerToken(String accountId, Token token) {
    checkArgument(!Strings.isNullOrEmpty(accountId));
    checkNotNull(token);

    return tokenRepository.registerToken(accountId, token);
  }

  private <T extends Token> T getCheckedToken(TokenInfo tokenInfo, Class<T> tokenClass) {
    if (tokenInfo == null) {
      return null;
    }

    // If someone fakes a token, at least it should ensure it hasn't expired
    // It saves us a database lookup.
    if (tokenInfo.getExp().isBefore(clock.currentTimeMillis())) {
      return null;
    }

    Token realToken = tokenRepository.getToken(tokenInfo.getId());
    if (realToken == null || !checkTokenValidity(realToken)) {
      // token does not exist or has expired
      return null;
    }

    if (!tokenClass.isInstance(realToken)) {
      return null;
    }

    return tokenClass.cast(realToken);
  }

  public <T extends Token> T getCheckedToken(String tokenSerial, Class<T> tokenClass) {
    return this.getCheckedToken(TokenSerializer.deserialize(tokenSerial), tokenClass);
  }
}
