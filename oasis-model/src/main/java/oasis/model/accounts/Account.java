package oasis.model.accounts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.annotations.Id;
import oasis.model.authn.Token;
import oasis.model.authz.AuthorizedScopes;

@JsonRootName("account")
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "type")
public abstract class Account {

  @ApiModelProperty(required = true)
  @Id
  private String id;

  @JsonProperty
  @ApiModelProperty()
  private List<Token> tokens = new ArrayList<>();

  @JsonProperty
  @ApiModelProperty
  private List<AuthorizedScopes> authorizedScopes = new ArrayList<>();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public List<Token> getTokens() {
    return Collections.unmodifiableList(tokens);
  }

  public void setTokens(List<Token> tokens) {
    this.tokens = new ArrayList<>(tokens);
  }

  public List<AuthorizedScopes> getAuthorizedScopes() {
    return Collections.unmodifiableList(authorizedScopes);
  }

  public void setAuthorizedScopes(List<AuthorizedScopes> authorizedScopes) {
    this.authorizedScopes = new ArrayList<>(authorizedScopes);
  }
}
