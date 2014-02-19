package oasis.services.authn;

import static com.google.common.base.Preconditions.*;

import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.Duration;

import com.google.api.client.util.Clock;
import com.google.common.base.Strings;

import oasis.model.authn.AccessToken;
import oasis.model.authn.AuthorizationCode;
import oasis.model.authn.RefreshToken;
import oasis.model.authn.SidToken;
import oasis.model.authn.Token;
import oasis.model.authn.TokenRepository;
import oasis.openidconnect.OpenIdConnectModule;

public class TokenHandler {
  private final TokenRepository tokenRepository;
  private final OpenIdConnectModule.Settings oidcSettings;
  private final Clock clock;

  @Inject TokenHandler(TokenRepository tokenRepository, OpenIdConnectModule.Settings oidcSettings, Clock clock) {
    this.tokenRepository = tokenRepository;
    this.oidcSettings = oidcSettings;
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

  public AccessToken createAccessToken(AuthorizationCode authorizationCode) {
    if (!tokenRepository.revokeToken(authorizationCode.getId())) {
      return null;
    }
    AccessToken accessToken = new AccessToken();
    accessToken.setAccountId(authorizationCode.getAccountId());
    accessToken.expiresIn(oidcSettings.accessTokenDuration);
    accessToken.setScopeIds(authorizationCode.getScopeIds());
    accessToken.setServiceProviderId(authorizationCode.getServiceProviderId());
    if (!tokenRepository.registerToken(authorizationCode.getAccountId(), accessToken)) {
      return null;
    }
    return accessToken;
  }

  public AccessToken createAccessToken(RefreshToken refreshToken, Set<String> scopeIds) {
    assert refreshToken.getScopeIds().containsAll(scopeIds);

    AccessToken accessToken = new AccessToken();
    accessToken.setAccountId(refreshToken.getAccountId());
    accessToken.expiresIn(oidcSettings.accessTokenDuration);
    accessToken.setScopeIds(scopeIds);
    accessToken.setServiceProviderId(refreshToken.getServiceProviderId());
    accessToken.setParent(refreshToken);

    if (!registerToken(refreshToken.getAccountId(), accessToken)) {
      return null;
    }

    // Return the new access token
    return accessToken;
  }

  public AuthorizationCode createAuthorizationCode(String accountId, Set<String> scopeIds, String serviceProviderId,
      @Nullable String nonce, String redirectUri) {
    checkArgument(!Strings.isNullOrEmpty(accountId));

    AuthorizationCode authorizationCode = new AuthorizationCode();
    authorizationCode.setAccountId(accountId);
    // A AuthorizationCode is available only for 1 minute
    authorizationCode.expiresIn(Duration.standardMinutes(1));
    authorizationCode.setScopeIds(scopeIds);
    authorizationCode.setServiceProviderId(serviceProviderId);
    authorizationCode.setNonce(nonce);
    authorizationCode.setRedirectUri(redirectUri);

    // Register the new token
    if (!registerToken(accountId, authorizationCode)) {
      return null;
    }

    // Return the new token
    return authorizationCode;
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

    SidToken sidToken = new SidToken();
    sidToken.setAccountId(accountId);
    sidToken.expiresIn(oidcSettings.sidTokenDuration);

    if (!registerToken(accountId, sidToken)) {
      return null;
    }

    return sidToken;
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
