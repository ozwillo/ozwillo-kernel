package oasis.web.authz;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

/*
 * Extracted from {@link JsonWebKeySet} for swagger.
 */
@ApiModel
class RsaJsonWebKey {
  @JsonProperty("kty")
  @ApiModelProperty(required = true)
  String keyType = "RSA";

  @JsonProperty("use")
  @ApiModelProperty(required = true)
  String keyUse = "sig";

  @JsonProperty("alg")
  @ApiModelProperty()
  String algorithm = "RS256";

  @JsonProperty("kid")
  @ApiModelProperty()
  String keyId;

  @JsonProperty("n")
  @ApiModelProperty(required = true, value = "Base 64 encoded string")
  String modulus;

  @JsonProperty("e")
  @ApiModelProperty(required = true, value = "Base 64 encoded string")
  String exponent;

  // For swagger
  public String getKeyType() {
    return keyType;
  }

  // For swagger
  public String getKeyUse() {
    return keyUse;
  }

  // For swagger
  public String getAlgorithm() {
    return algorithm;
  }

  // For swagger
  public String getKeyId() {
    return keyId;
  }

  // For swagger
  public String getModulus() {
    return modulus;
  }

  // For swagger
  public String getExponent() {
    return exponent;
  }
}
