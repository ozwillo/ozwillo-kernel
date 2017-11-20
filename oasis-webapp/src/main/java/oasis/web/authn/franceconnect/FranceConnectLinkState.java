/**
 * Ozwillo Kernel
 * Copyright (C) 2017  The Ozwillo Kernel Authors
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
package oasis.web.authn.franceconnect;

import java.io.IOException;

import org.immutables.value.Value;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.lang.JoseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import oasis.auth.AuthModule;

@Value.Immutable
@JsonSerialize(as = ImmutableFranceConnectLinkState.class)
@JsonDeserialize(as = ImmutableFranceConnectLinkState.class)
abstract class FranceConnectLinkState {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static FranceConnectLinkState create(String access_token, String id_token, String franceconnect_sub) {
    return ImmutableFranceConnectLinkState.builder()
        .access_token(access_token)
        .id_token(id_token)
        .franceconnect_sub(franceconnect_sub)
        .build();
  }

  abstract String access_token();
  abstract String id_token();
  abstract String franceconnect_sub();

  String encrypt(AuthModule.Settings settings) throws JoseException, JsonProcessingException {
    JsonWebEncryption jwe = new JsonWebEncryption();
    jwe.setPlaintext(OBJECT_MAPPER.writeValueAsBytes(this));
    jwe.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.RSA1_5);
    jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256);
    jwe.setKey(settings.keyPair.getPublic());
    return jwe.getCompactSerialization();
  }

  static FranceConnectLinkState decrypt(AuthModule.Settings settings, String encoded) throws JoseException, IOException {
    JsonWebEncryption jwe = new JsonWebEncryption();
    jwe.setAlgorithmConstraints(new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.WHITELIST, KeyManagementAlgorithmIdentifiers.RSA1_5));
    jwe.setContentEncryptionAlgorithmConstraints(new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.WHITELIST, ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256));
    jwe.setCompactSerialization(encoded);
    jwe.setKey(settings.keyPair.getPrivate());
    return OBJECT_MAPPER.readValue(jwe.getPlaintextBytes(), ImmutableFranceConnectLinkState.class);
  }
}
