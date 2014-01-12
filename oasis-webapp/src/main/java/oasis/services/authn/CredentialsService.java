package oasis.services.authn;

import javax.inject.Inject;

import oasis.model.authn.ClientType;
import oasis.model.authn.Credentials;
import oasis.model.authn.CredentialsRepository;
import oasis.services.authn.login.PasswordHasher;
import oasis.services.authn.login.SShaPasswordHasher;

public class CredentialsService {
  private final CredentialsRepository credentialsRepository;
  private final PasswordHasher passwordHasher;

  @Inject
  CredentialsService(CredentialsRepository credentialsRepository, SShaPasswordHasher passwordHasher) {
    this.credentialsRepository = credentialsRepository;
    this.passwordHasher = passwordHasher;
  }

  public void setPassword(ClientType type, String id, String password) {
    byte[] salt = passwordHasher.createSalt();
    byte[] hash = passwordHasher.hashPassword(password, salt);
    credentialsRepository.saveCredentials(type, id, hash, salt);
  }

  public boolean checkPassword(ClientType type, String id, String password) {
    Credentials credentials = credentialsRepository.getCredentials(type, id);
    if (credentials == null) {
      return false;
    }
    return passwordHasher.checkPassword(password, credentials.getHash(), credentials.getSalt());
  }
}
