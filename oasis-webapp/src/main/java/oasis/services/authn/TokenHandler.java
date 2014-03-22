package oasis.services.authn;

import static com.google.common.base.Preconditions.*;

import java.security.SecureRandom;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.Duration;

import com.google.api.client.util.Clock;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;

import oasis.model.authn.AccessToken;
import oasis.model.authn.AuthorizationCode;
import oasis.model.authn.RefreshToken;
import oasis.model.authn.SidToken;
import oasis.model.authn.Token;
import oasis.model.authn.TokenRepository;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.services.authn.login.PasswordHasher;

public class TokenHandler {
  private static final BaseEncoding BASE_ENCODING = BaseEncoding.base64Url().omitPadding();
  // Note: make sure BASE_ENCODING won't ever produce such a character
  private static final String SEPARATOR = "/";

  private final TokenRepository tokenRepository;
  private final OpenIdConnectModule.Settings oidcSettings;
  private final PasswordHasher passwordHasher;
  private final SecureRandom secureRandom;
  private final Clock clock;

  @Inject TokenHandler(TokenRepository tokenRepository, OpenIdConnectModule.Settings oidcSettings,
      PasswordHasher passwordHasher, SecureRandom secureRandom, Clock clock) {
    this.tokenRepository = tokenRepository;
    this.oidcSettings = oidcSettings;
    this.passwordHasher = passwordHasher;
    this.secureRandom = secureRandom;
    this.clock = clock;
  }

  public String generateRandom() {
    byte[] bytes = new byte[16]; // 128bits
    secureRandom.nextBytes(bytes);
    return BASE_ENCODING.encode(bytes);
  }

  public AccessToken createAccessToken(AuthorizationCode authorizationCode, String pass) {
    if (!tokenRepository.revokeToken(authorizationCode.getId())) {
      return null;
    }
    AccessToken accessToken = new AccessToken();
    accessToken.setAccountId(authorizationCode.getAccountId());
    accessToken.expiresIn(oidcSettings.accessTokenDuration);
    accessToken.setScopeIds(authorizationCode.getScopeIds());
    accessToken.setServiceProviderId(authorizationCode.getServiceProviderId());

    secureToken(accessToken, pass);

    if (!tokenRepository.registerToken(authorizationCode.getAccountId(), accessToken)) {
      return null;
    }
    return accessToken;
  }

  public AccessToken createAccessToken(RefreshToken refreshToken, Set<String> scopeIds, String pass) {
    assert refreshToken.getScopeIds().containsAll(scopeIds);

    AccessToken accessToken = new AccessToken();
    accessToken.setAccountId(refreshToken.getAccountId());
    accessToken.expiresIn(oidcSettings.accessTokenDuration);
    accessToken.setScopeIds(scopeIds);
    accessToken.setServiceProviderId(refreshToken.getServiceProviderId());
    accessToken.setParent(refreshToken);

    secureToken(accessToken, pass);

    if (!tokenRepository.registerToken(refreshToken.getAccountId(), accessToken)) {
      return null;
    }

    // Return the new access token
    return accessToken;
  }

  public AuthorizationCode createAuthorizationCode(String accountId, Set<String> scopeIds, String serviceProviderId,
      @Nullable String nonce, String redirectUri, boolean shouldSendAuthTime, String pass) {
    checkArgument(!Strings.isNullOrEmpty(accountId));

    AuthorizationCode authorizationCode = new AuthorizationCode();
    authorizationCode.setAccountId(accountId);
    // A AuthorizationCode is available only for 1 minute
    authorizationCode.expiresIn(Duration.standardMinutes(1));
    authorizationCode.setScopeIds(scopeIds);
    authorizationCode.setServiceProviderId(serviceProviderId);
    authorizationCode.setNonce(nonce);
    authorizationCode.setRedirectUri(redirectUri);
    authorizationCode.setShouldSendAuthTime(shouldSendAuthTime);

    secureToken(authorizationCode, pass);

    // Register the new token
    if (!tokenRepository.registerToken(accountId, authorizationCode)) {
      return null;
    }

    // Return the new token
    return authorizationCode;
  }

  public RefreshToken createRefreshToken(AuthorizationCode authorizationCode, String pass) {
    if (!tokenRepository.revokeToken(authorizationCode.getId())) {
      return null;
    }

    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setAccountId(authorizationCode.getAccountId());
    // A RefreshToken is valid 50 years
    refreshToken.expiresIn(Duration.standardDays(50 * 365));
    refreshToken.setScopeIds(authorizationCode.getScopeIds());
    refreshToken.setServiceProviderId(authorizationCode.getServiceProviderId());

    secureToken(refreshToken, pass);

    // Register the new token
    if (!tokenRepository.registerToken(authorizationCode.getAccountId(), refreshToken)) {
      return null;
    }

    return refreshToken;
  }

  public SidToken createSidToken(String accountId, String pass) {
    checkArgument(!Strings.isNullOrEmpty(accountId));

    SidToken sidToken = new SidToken();
    sidToken.setAccountId(accountId);
    sidToken.expiresIn(oidcSettings.sidTokenDuration);
    // TODO: remember me
    sidToken.setAuthenticationTime(sidToken.getCreationTime());

    secureToken(sidToken, pass);

    if (!tokenRepository.registerToken(accountId, sidToken)) {
      return null;
    }

    return sidToken;
  }

  private <T extends Token> T getCheckedToken(TokenInfo tokenInfo, Class<T> tokenClass) {
    if (tokenInfo == null) {
      return null;
    }

    // If someone fakes a token, at least they should ensure it hasn't expired
    // It saves us a database lookup.
    if (tokenInfo.getExp().isBefore(clock.currentTimeMillis())) {
      return null;
    }

    String[] splitId = splitId(tokenInfo.getId());
    if (splitId == null) {
      return null;
    }
    Token realToken = tokenRepository.getToken(splitId[0]);
    if (realToken == null || !checkTokenValidity(realToken, splitId[1])) {
      // token does not exist, has expired, or is otherwise invalid
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

  private void secureToken(Token token, String pass) {
    byte[] salt = passwordHasher.createSalt();
    byte[] hash = passwordHasher.hashPassword(pass, salt);

    token.setHash(hash);
    token.setSalt(salt);
  }

  // Used by TokenInfo
  static String makeId(String id, String pass) {
    assert pass != null;
    return id + SEPARATOR + pass;
  }

  private static String[] splitId(String id) {
    if (id == null) {
      return null;
    }
    int pos = id.indexOf(SEPARATOR);
    if (pos < 0) {
      return null;
    }
    return new String[] {
        id.substring(0, pos),
        id.substring(pos + 1)
    };
  }

  private boolean checkTokenValidity(Token token, String pass) {
    // A null token is not valid !
    if (token == null) {
      return false;
    }

    if (token.getExpirationTime().isBefore(clock.currentTimeMillis())) {
      // Token is expired
      return false;
    }

    return passwordHasher.checkPassword(pass, token.getHash(), token.getSalt());
  }
}
