package oasis.services.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.Iterator;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;

import oasis.model.accounts.Token;
import oasis.model.accounts.TokenViews;

public class TokenSerializer {
  /**
   * Create a string containing various token info, ready to give to the client.
   * @param token The token we need to serialize
   * @return A base64 encoded json object containg : ID, Creation Time and TTL
   * @throws com.fasterxml.jackson.core.JsonProcessingException
   */
  public static String serialize(Token token) throws JsonProcessingException {
    // We can't serialize a null token
    if ( token == null ) {
      return null;
    }

    ObjectMapper objectMapper = new ObjectMapper();
    BaseEncoding base64Encoder = BaseEncoding.base64();

    Map<String, Object> tokenMap = ImmutableMap.<String, Object>of(
        "id", token.getId(),
        "creationTime", token.getCreationTime(),
        "timeToLive", token.getTimeToLive()
    );

    // Return base64 encoded json
    return base64Encoder.encode(objectMapper.writeValueAsBytes(tokenMap));
  }

  /**
   * Convert a string created with serialize() to a partially filled Token object
   * @param tokenSerial String created with serialize()
   * @return A partially filled Token object usable for authentication
   */
  public static Token unserialize(String tokenSerial) throws IOException {
    BaseEncoding base64Encoder = BaseEncoding.base64();
    ObjectMapper objectMapper = new ObjectMapper();

    Iterator<Token> tokens = objectMapper.readerWithView(TokenViews.Serializer.class).withType(Token.class).readValues(base64Encoder.decode(tokenSerial));

    if ( tokens.hasNext() ) {
      return tokens.next();
    }

    throw new InvalidParameterException();
  }
}
