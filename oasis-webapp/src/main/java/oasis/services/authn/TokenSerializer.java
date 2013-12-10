package oasis.services.authn;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.io.BaseEncoding;

import oasis.model.accounts.Token;
import oasis.model.accounts.TokenViews;

public class TokenSerializer {
  private static final Logger logger = LoggerFactory.getLogger(TokenSerializer.class);
  private static final BaseEncoding BASE_ENCODING = BaseEncoding.base64Url().omitPadding();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JodaModule());
  private static final ObjectWriter OBJECT_WRITER = OBJECT_MAPPER.writerWithView(TokenViews.Serializer.class);
  private static final ObjectReader OBJECT_READER = OBJECT_MAPPER.readerWithView(TokenViews.Serializer.class);

  /**
   * Create a string containing various token info, ready to give to the client.
   *
   * @param token The token we need to serialize
   * @return A base64 encoded json object containing : ID, Creation Time and TTL.
   */
  public static String serialize(Token token) {
    // We can't serialize a null token
    if (token == null) {
      return null;
    }

    // Return base64 encoded json
    try {
      return BASE_ENCODING.encode(OBJECT_WRITER.writeValueAsBytes(token));
    } catch (JsonProcessingException e) {
      logger.error("Can't serialize the given token {}", token.getId(), e);
      return null;
    }
  }

  /**
   * Convert a string created with serialize() to a partially filled Token object
   *
   * @param tokenSerial String created with serialize()
   * @return A partially filled Token object usable for authentication
   */
  public static Token unserialize(String tokenSerial) {
    try {
      return OBJECT_READER.withType(Token.class).readValue(BASE_ENCODING.decode(tokenSerial));
    } catch (IOException e) {
      logger.error("Can't unserialize the given string {}", tokenSerial, e);
      return null;
    }
  }
}
