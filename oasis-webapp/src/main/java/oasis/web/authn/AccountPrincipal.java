package oasis.web.authn;

import java.security.Principal;

import javax.annotation.concurrent.NotThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oasis.model.accounts.Account;
import oasis.model.accounts.AccountRepository;

@NotThreadSafe
public class AccountPrincipal implements Principal {
  private static final Logger logger = LoggerFactory.getLogger(AccountPrincipal.class);
  private final AccountRepository accountRepository;
  private final String accountId;
  private Account account;
  private boolean accountRetrieved = false;

  public AccountPrincipal(AccountRepository accountRepository, String accountId) {
    this.accountRepository = accountRepository;
    this.accountId = accountId;
  }

  @Override
  public String getName() {
    return accountId;
  }

  public Account getAccount() {
    if (!accountRetrieved) {
      account = accountRepository.getAccount(accountId);
      accountRetrieved = true;

      if (account == null) {
        logger.error("No Account found for ID {}.", accountId);
      }
    }

    return account;
  }
}
