package oasis.model.accounts;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.wordnik.swagger.annotations.ApiModelProperty;
import oasis.model.annotations.Id;

@JsonRootName("account")
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "type")
public abstract class Account {

  @ApiModelProperty(required = true)
  @Id
  private String id;

  @JsonProperty
  @ApiModelProperty()
  private List<Token> tokens;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public List<Token> getTokens() {
    if ( tokens == null ) {
      tokens = new ArrayList<>();
    }

    return tokens;
  }

  public void setTokens(List<Token> tokens) {
    this.tokens = tokens;
  }

  public void addToken(Token token) {
    this.getTokens().add(token);
  }

  public void removeToken(Token token) {
    if ( token == null ) {
      return;
    }

    this.getTokens().remove(token);
  }
}
