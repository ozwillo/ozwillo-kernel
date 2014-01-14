package oasis.model.authn;

public interface CredentialsRepository {
  Credentials saveCredentials(ClientType type, String id, byte[] hash, byte[] salt);

  Credentials getCredentials(ClientType type, String id);
}
