/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
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
package oasis.services.authn;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.io.BaseEncoding;

import oasis.model.authn.Token;

public class TokenSerializer {
  private static final Logger logger = LoggerFactory.getLogger(TokenSerializer.class);
  private static final BaseEncoding BASE_ENCODING = BaseEncoding.base64Url().omitPadding();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

  public static String serialize(Token token, String pass) {
    // We can't serialize a null token
    if (token == null) {
      return null;
    }

    // Return base64 encoded json
    try {
      return BASE_ENCODING.encode(OBJECT_MAPPER.writeValueAsBytes(new TokenInfo(token, pass)));
    } catch (JsonProcessingException e) {
      logger.error("Can't serialize the given token {}", token.getId(), e);
      return null;
    }
  }

  public static TokenInfo deserialize(String tokenSerial) {
    try {
      return OBJECT_MAPPER.readValue(BASE_ENCODING.decode(tokenSerial), TokenInfo.class);
    } catch (IOException | IllegalArgumentException e) {
      logger.error("Can't deserialize the given string {}", tokenSerial, e);
      return null;
    }
  }
}
