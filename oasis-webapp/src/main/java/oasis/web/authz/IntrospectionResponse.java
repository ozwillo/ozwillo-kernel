package oasis.web.authz;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModelProperty;

class IntrospectionResponse {
  @JsonProperty
  @ApiModelProperty
  private boolean active;
  @JsonProperty
  @ApiModelProperty
  private long exp;
  @JsonProperty
  @ApiModelProperty
  private long iat;
  @JsonProperty
  @ApiModelProperty
  private String scope;
  @JsonProperty
  @ApiModelProperty
  private String client_id;
  @JsonProperty
  @ApiModelProperty
  private String sub;
  @JsonProperty
  @ApiModelProperty
  private String aud;
  @JsonProperty
  @ApiModelProperty
  private String token_type;

  public boolean isActive() {
    return active;
  }

  IntrospectionResponse setActive(boolean active) {
    this.active = active;
    return this;
  }

  public long getExp() {
    return exp;
  }

  IntrospectionResponse setExp(long exp) {
    this.exp = exp;
    return this;
  }

  public long getIat() {
    return iat;
  }

  IntrospectionResponse setIat(long iat) {
    this.iat = iat;
    return this;
  }

  public String getScope() {
    return scope;
  }

  IntrospectionResponse setScope(String scope) {
    this.scope = scope;
    return this;
  }

  public String getClient_id() {
    return client_id;
  }

  IntrospectionResponse setClient_id(String client_id) {
    this.client_id = client_id;
    return this;
  }

  public String getSub() {
    return sub;
  }

  IntrospectionResponse setSub(String sub) {
    this.sub = sub;
    return this;
  }

  public String getAud() {
    return aud;
  }

  IntrospectionResponse setAud(String aud) {
    this.aud = aud;
    return this;
  }

  public String getToken_type() {
    return token_type;
  }

  IntrospectionResponse setToken_type(String token_type) {
    this.token_type = token_type;
    return this;
  }
}