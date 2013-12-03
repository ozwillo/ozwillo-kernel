package oasis.web.authz;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

/*
 * Extracted from {@link JsonWebKeySet} for swagger.
 */
@ApiModel
class JsonWebKeySet {
  @JsonProperty("keys")
  @ApiModelProperty(required = true)
  RsaJsonWebKey[] rsaJsonWebKeys;

  // For swagger
  public RsaJsonWebKey[] getRsaJsonWebKeys() {
    return rsaJsonWebKeys;
  }
}
