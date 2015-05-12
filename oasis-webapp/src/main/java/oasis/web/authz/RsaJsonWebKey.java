/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
