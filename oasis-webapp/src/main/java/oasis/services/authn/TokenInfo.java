package oasis.services.authn;

import static oasis.services.authn.TokenHandler.makeId;

import org.joda.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import oasis.model.authn.Token;

public class TokenInfo {
  @JsonProperty
  private String id;
  @JsonProperty
  private Instant iat;
  @JsonProperty
  private Instant exp;

  public TokenInfo() {
  }

  public TokenInfo(Token token, String pass) {
    this.id = makeId(token.getId(), pass);
    this.iat = token.getCreationTime();
    this.exp = token.getExpirationTime();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Instant getIat() {
    return iat;
  }

  public void setIat(Instant iat) {
    this.iat = iat;
  }

  public Instant getExp() {
    return exp;
  }

  public void setExp(Instant exp) {
    this.exp = exp;
  }
}
