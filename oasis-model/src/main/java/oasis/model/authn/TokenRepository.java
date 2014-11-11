package oasis.model.authn;

import java.util.Collection;

public interface TokenRepository {
  Token getToken(String tokenId);

  boolean registerToken(Token token);

  boolean revokeToken(String tokenId);

  boolean renewToken(String tokenId);

  boolean reAuthSidToken(String tokenId);

  int revokeTokensForAccount(String accountId);

  int revokeTokensForAccountAndTokenType(String accountId, Class<? extends Token> tokenType);

  int revokeTokensForClient(String clientId);

  int revokeTokensForScopes(Collection<String> scopeIds);

  Collection<String> getAllClientsForSession(String sidTokenId);
}
