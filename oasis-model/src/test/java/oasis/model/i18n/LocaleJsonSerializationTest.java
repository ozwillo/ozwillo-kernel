package oasis.model.i18n;

import static org.assertj.core.api.Assertions.*;

import java.util.Locale;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class LocaleJsonSerializationTest {
  public static class Foo {
    @JsonProperty Locale locale;
  }

  @Test public void testSerialization() throws Exception {
    Foo foo = new Foo();
    foo.locale = Locale.FRANCE;

    ObjectMapper mapper = new ObjectMapper()
        .registerModule(new LocalizableModule());
    ObjectNode node = mapper.convertValue(foo, ObjectNode.class);

    assertThat(node.get("locale"))
        .describedAs(mapper.writeValueAsString(foo))
        .isEqualTo(mapper.getNodeFactory().textNode(Locale.FRANCE.toLanguageTag()));
  }

  @Test public void testDeserialization() throws Exception {
    ObjectMapper mapper = new ObjectMapper()
        .registerModule(new LocalizableModule());
    Foo foo = mapper.readValue("{\"locale\": \"en-GB\"}", Foo.class);

    assertThat(foo.locale).isEqualTo(Locale.UK);
  }
}
