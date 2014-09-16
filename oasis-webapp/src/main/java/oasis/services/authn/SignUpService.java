package oasis.services.authn;

import java.util.Locale;

import javax.inject.Inject;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.Address;
import oasis.model.accounts.UserAccount;

public class SignUpService {
  private final AccountRepository accountRepository;
  private final UserPasswordAuthenticator userPasswordAuthenticator;

  @Inject SignUpService(AccountRepository accountRepository, UserPasswordAuthenticator userPasswordAuthenticator) {
    this.accountRepository = accountRepository;
    this.userPasswordAuthenticator = userPasswordAuthenticator;
  }

  public UserAccount signUp(String email, String password, String nickname, Locale locale) {
    UserAccount userAccount = new UserAccount();
    userAccount.setEmail_address(email);
    // FIXME: temporarily auto-verify the e-mail address
    userAccount.setEmail_verified(true);
    userAccount.setNickname(nickname);
    userAccount.setLocale(locale.toLanguageTag());
    // TODO: Use a zoneinfo "matching" the selected locale
    userAccount.setZoneinfo("Europe/Paris");
    userAccount = accountRepository.createUserAccount(userAccount);
    if (userAccount != null) {
      userPasswordAuthenticator.setPassword(userAccount.getId(), password);
    }
    return userAccount;
  }
}
