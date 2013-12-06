package oasis.web.authn;

import java.security.Principal;

import javax.annotation.concurrent.NotThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oasis.model.accounts.AccessToken;
import oasis.model.accounts.Account;
import oasis.model.accounts.AccountRepository;

@NotThreadSafe
public class OAuthPrincipal implements Principal {
  private static final Logger logger = LoggerFactory.getLogger(OAuthPrincipal.class);

  private final AccountRepository accountRepository;
  private final AccessToken accessToken;
  private boolean accountRetrieved = false;
  private Account account;

  public OAuthPrincipal(AccountRepository accountRepository, AccessToken accessToken) {
    this.accountRepository = accountRepository;
    this.accessToken = accessToken;
  }

  @Override
  public String getName() {
    return accessToken.getId();
  }

  public Account getAccount() {
    if (!accountRetrieved) {
      account = accountRepository.getAccountByTokenId(accessToken.getId());
      accountRetrieved = true;

      if (account == null) {
        logger.warn("No Account found for validated AccessToken.");
      }
    }

    return account;
  }

  public AccessToken getAccessToken() {
    return accessToken;
  }
}
