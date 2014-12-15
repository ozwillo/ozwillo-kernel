package oasis.jongo.accounts;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.jongo.etag.HasModified;
import oasis.model.accounts.UserAccount;

public class JongoUserAccount extends UserAccount implements HasModified {

  @JsonProperty
  private Long activated_at; // XXX: not exposed, only initialized once

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

  public void initCreated_at() {
    setCreated_at(System.currentTimeMillis());
  }

  public void initActivated_at() {
    activated_at = System.currentTimeMillis();
  }
}
