package oasis.services.authn;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.io.BaseEncoding;

import oasis.model.authn.Token;

public class TokenSerializer {
  private static final Logger logger = LoggerFactory.getLogger(TokenSerializer.class);
  private static final BaseEncoding BASE_ENCODING = BaseEncoding.base64Url().omitPadding();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JodaModule());

  public static String serialize(Token token) {
    // We can't serialize a null token
    if (token == null) {
      return null;
    }

    // Return base64 encoded json
    try {
      return BASE_ENCODING.encode(OBJECT_MAPPER.writeValueAsBytes(new TokenInfo(token)));
    } catch (JsonProcessingException e) {
      logger.error("Can't serialize the given token {}", token.getId(), e);
      return null;
    }
  }

  public static TokenInfo deserialize(String tokenSerial) {
    try {
      return OBJECT_MAPPER.readValue(BASE_ENCODING.decode(tokenSerial), TokenInfo.class);
    } catch (IOException e) {
      logger.error("Can't deserialize the given string {}", tokenSerial, e);
      return null;
    }
  }
}
