/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.jongo.accounts;

import java.util.Map;

import javax.inject.Inject;

import org.jongo.FindAndModify;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.primitives.Longs;
import com.mongodb.DuplicateKeyException;

import oasis.jongo.JongoBootstrapper;
import oasis.model.InvalidVersionException;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.Address;
import oasis.model.accounts.UserAccount;

public class JongoAccountRepository implements AccountRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(JongoAccountRepository.class);

  private final Jongo jongo;

  @Inject
  JongoAccountRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  protected MongoCollection getAccountCollection() {
    return jongo.getCollection("account");
  }

  @Override
  public UserAccount getUserAccountByEmail(String email) {
    // XXX: accounts aren't activated until you verify the e-mail address
    return this.getAccountCollection()
        .findOne("{ email_address: #, email_verified: true }", email)
        .as(JongoUserAccount.class);
  }

  @Override
  public UserAccount getUserAccountById(String id) {
    // XXX: accounts aren't activated until you verify the e-mail address
    return this.getAccountCollection()
        .findOne("{ id: #, email_verified: true }", id)
        .as(JongoUserAccount.class);
  }

  @Override
  public UserAccount createUserAccount(UserAccount user) {
    JongoUserAccount jongoUserAccount = new JongoUserAccount(user);
    jongoUserAccount.initCreated_at();
    try {
      getAccountCollection().insert(jongoUserAccount);
    } catch (DuplicateKeyException e) {
      return null;
    }
    return jongoUserAccount;
  }

  @Override
  public UserAccount updateAccount(UserAccount account, long[] versions) throws InvalidVersionException {
    String id = account.getId();
    Preconditions.checkArgument(!Strings.isNullOrEmpty(id));
    // Copy to get the updated_at field, and reset ID (not copied over) to make sure we won't generate a new one
    account = new JongoUserAccount(account);
    account.setId(id);
    // XXX: don't allow modifying the email address, phone number verified, or created_at
    account.setEmail_address(null);
    account.setEmail_verified(null);
    account.setPhone_number_verified(null);
    account.setCreated_at(null);
    // nullify address if "empty"
    if (account.getAddress() != null
        && Strings.isNullOrEmpty(account.getAddress().getStreet_address())
        && Strings.isNullOrEmpty(account.getAddress().getPostal_code())
        && Strings.isNullOrEmpty(account.getAddress().getLocality())
        && Strings.isNullOrEmpty(account.getAddress().getRegion())
        && Strings.isNullOrEmpty(account.getAddress().getCountry())) {
      account.setAddress(null);
    }
    // Allow resetting fields to null/empty
    // TODO: find a better way to do it! (leveraging Jackson)
    Map<String, Boolean> unsetObject = Maps.newLinkedHashMap();
    // NOTE: don't allow resetting the nickname and locale (set during account creation), and zoneinfo
    if (Strings.isNullOrEmpty(account.getName())) {
      unsetObject.put("name", true);
    }
    if (Strings.isNullOrEmpty(account.getFamily_name())) {
      unsetObject.put("family_name", true);
    }
    if (Strings.isNullOrEmpty(account.getMiddle_name())) {
      unsetObject.put("middle_name", true);
    }
    if (Strings.isNullOrEmpty(account.getGiven_name())) {
      unsetObject.put("given_name", true);
    }
    if (Strings.isNullOrEmpty(account.getPicture())) {
      unsetObject.put("picture", true);
    }
    if (account.getBirthdate() == null) {
      unsetObject.put("birthdate", true);
    }
    if (Strings.isNullOrEmpty(account.getGender())) {
      unsetObject.put("gender", true);
    }
    if (Strings.isNullOrEmpty(account.getPhone_number())) {
      unsetObject.put("phone_number", true);
    }
    if (account.getAddress() == null) {
      unsetObject.put("address", true);
    }

    account = getAccountCollection()
        .findAndModify("{ id: #, updated_at: { $in: # } }", id, Longs.asList(versions))
        // MongoDB rejects empty modifiers: https://jira.mongodb.org/browse/SERVER-12266
        .with(unsetObject.isEmpty() ? "{ $set: # }"           : "{ $set: #, $unset: # }",
              unsetObject.isEmpty() ? new Object[]{ account } : new Object[] { account, unsetObject })
        .returnNew()
        .as(JongoUserAccount.class);
    if (account == null) {
      if (getAccountCollection().count("{ id: # }", id) != 0) {
        throw new InvalidVersionException("account", id);
      }
      return null;
    }
    return account;
  }

  @Override
  public UserAccount verifyEmailAddress(String id) {
    // XXX: we use a JongoUserAccount to update the updated_at field
    JongoUserAccount userAccount = new JongoUserAccount();
    // reset ID (not copied over) to make sure we won't generate a new one
    userAccount.setId(id);
    userAccount.setEmail_verified(true);
    userAccount.initActivated_at();
    return getAccountCollection()
        .findAndModify("{ id: # }", id)
        .with("{ $set: # }", userAccount)
        .returnNew()
        .as(JongoUserAccount.class);
  }

  @Override
  public boolean deleteUserAccount(String id) {
    return getAccountCollection()
        .remove("{ id: # }", id)
        .getN() > 0;
  }

  @Override
  public void bootstrap() {
    getAccountCollection().ensureIndex("{ id : 1 }", "{ unique: 1 }");
    getAccountCollection().ensureIndex("{ email_address : 1 }", "{ unique: 1, sparse: 1 }");
  }
}

