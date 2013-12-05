package oasis.services.authn;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.BaseEncoding;

import oasis.model.accounts.Token;
import oasis.model.accounts.TokenViews;

public class TokenSerializer {
  private static final Logger logger = LoggerFactory.getLogger(TokenSerializer.class);

  /**
   * Create a string containing various token info, ready to give to the client.
   * @param token The token we need to serialize
   * @return A base64 encoded json object containg : ID, Creation Time and TTL.
   */
  public static String serialize(Token token) {
    // We can't serialize a null token
    if ( token == null ) {
      return null;
    }

    ObjectMapper objectMapper = new ObjectMapper();
    BaseEncoding base64Encoder = BaseEncoding.base64();

    // Return base64 encoded json
    try {
      return base64Encoder.encode(objectMapper.writerWithView(TokenViews.Serializer.class).writeValueAsBytes(token));
    } catch (JsonProcessingException e) {
      logger.error("Can't serialize the given token {}", token.getId(), e);
      return null;
    }
  }

  /**
   * Convert a string created with serialize() to a partially filled Token object
   * @param tokenSerial String created with serialize()
   * @return A partially filled Token object usable for authentication
   */
  public static Token unserialize(String tokenSerial) {
    BaseEncoding base64Encoder = BaseEncoding.base64();
    ObjectMapper objectMapper = new ObjectMapper();

    try {
      return objectMapper.readerWithView(TokenViews.Serializer.class).withType(Token.class).readValue(base64Encoder.decode(tokenSerial));
    } catch (IOException e) {
      logger.error("Can't unserialize the given string {}", tokenSerial, e);
      return null;
    }
  }
}
