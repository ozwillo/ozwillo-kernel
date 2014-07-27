package oasis.jongo.accounts;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import oasis.jongo.etag.HasModified;
import oasis.model.accounts.UserAccount;

public class JongoUserAccount extends UserAccount implements HasModified {
  public JongoUserAccount() {
    setUpdated_at(System.currentTimeMillis());
  }

  public JongoUserAccount(@Nonnull UserAccount other) {
    super(other);
    setUpdated_at(System.currentTimeMillis());
  }

  @Override
  @JsonIgnore
  public long getModified() {
    return getUpdated_at();
  }
}
