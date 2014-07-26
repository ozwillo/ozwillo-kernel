package oasis.model.authz;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonRootName;

import oasis.model.annotations.Id;

@JsonRootName("authorizedScopes")
public class AuthorizedScopes {
  @Id
  private String id;
  private String account_id;
  private String client_id;
  private Set<String> scope_ids = new HashSet<>();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getAccount_id() {
    return account_id;
  }

  public void setAccount_id(String account_id) {
    this.account_id = account_id;
  }

  public String getClient_id() {
    return client_id;
  }

  public void setClient_id(String client_id) {
    this.client_id = client_id;
  }

  public Set<String> getScope_ids() {
    return Collections.unmodifiableSet(scope_ids);
  }

  public void setScope_ids(Set<String> scope_ids) {
    this.scope_ids = new HashSet<>(scope_ids);
  }
}
