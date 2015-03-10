package oasis.model.authn;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public abstract class AbstractAccountToken extends Token {
  @JsonProperty
  private String accountId;

  public String getAccountId() {
    return accountId;
  }

  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  @Override
  public void checkValidity() {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(accountId));
  }
}
