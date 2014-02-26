package oasis.model.authn;

public interface TokenRepository {
  Token getToken(String tokenId);

  boolean registerToken(String accountId, Token token);

  boolean revokeToken(String tokenId);

  boolean renewToken(String tokenId);
}
