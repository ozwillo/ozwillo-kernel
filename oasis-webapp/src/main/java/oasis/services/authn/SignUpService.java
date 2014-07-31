package oasis.services.authn;

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

  public UserAccount signUp(String email, String password, String zipcode, String country) {
    UserAccount userAccount = new UserAccount();
    userAccount.setEmail_address(email);
    Address address = new Address();
    address.setCountry(country);
    address.setPostal_code(zipcode);
    userAccount.setAddress(address);
    // TODO: Set the locale with the locale selected
    userAccount.setLocale("en-GB");
    // XXX: Set the zone info from the country?
    userAccount.setZoneinfo("Europe/Paris");
    userAccount = accountRepository.createUserAccount(userAccount);
    if (userAccount != null) {
      userPasswordAuthenticator.setPassword(userAccount.getId(), password);
    }
    return userAccount;
  }
}