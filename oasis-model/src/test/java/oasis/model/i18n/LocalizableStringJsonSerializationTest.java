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
package oasis.model.i18n;

import static org.assertj.core.api.Assertions.*;

import java.util.Locale;

import org.assertj.core.api.Condition;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.icu.util.ULocale;

public class LocalizableStringJsonSerializationTest {

  public static class Foo {
    @JsonProperty
    LocalizableString localized;

    private final LocalizableString setterless = new LocalizableString();

    @JsonProperty
    public LocalizableString getSetterless() {
      return setterless;
    }
  }

  @Test
  public void testSerialization() throws Exception {
    Foo foo = new Foo();
    foo.localized = new LocalizableString();
    foo.localized.set(ULocale.ROOT, "root");
    foo.localized.set(ULocale.FRENCH, "Français");
    foo.localized.set(ULocale.ENGLISH, "English");

    ObjectMapper mapper = new ObjectMapper()
        .registerModule(new LocalizableModule());
    ObjectNode node = mapper.convertValue(foo, ObjectNode.class);

    assertThat(node.fieldNames())
        .contains("localized", "localized#fr", "localized#en")
        .haveAtMost(2, new Condition<String>() {
          @Override
          public boolean matches(String value) {
            return value.startsWith("localized#");
          }
        });

    assertThat(node.get("localized")).isEqualTo(mapper.getNodeFactory().textNode("root"));
    assertThat(node.get("localized#fr")).isEqualTo(mapper.getNodeFactory().textNode("Français"));
    assertThat(node.get("localized#en")).isEqualTo(mapper.getNodeFactory().textNode("English"));
  }

  @Test
  public void testDeserialization() throws Exception {
    ObjectNode node = new ObjectNode(JsonNodeFactory.instance)
        .put("localized", "root")
        .put("localized#fr", "Français")
        .put("localized#en", "English");

    ObjectMapper mapper = new ObjectMapper()
        .registerModule(new LocalizableModule());

    Foo foo = mapper.convertValue(node, Foo.class);

    assertThat(foo).isNotNull();
    assertThat(foo.localized).isNotNull();
    assertThat(foo.localized.values).containsOnly(
        entry(ULocale.ROOT, "root"),
        entry(ULocale.FRENCH, "Français"),
        entry(ULocale.ENGLISH, "English")
    );
  }

  @Test
  public void testSetterlessSerialization() throws Exception {
    Foo foo = new Foo();
    foo.getSetterless().set(ULocale.ROOT, "root");
    foo.getSetterless().set(ULocale.FRENCH, "Français");
    foo.getSetterless().set(ULocale.ENGLISH, "English");

    ObjectMapper mapper = new ObjectMapper()
        .registerModule(new LocalizableModule());
    ObjectNode node = mapper.convertValue(foo, ObjectNode.class);

    assertThat(node.fieldNames())
        .contains("setterless", "setterless#fr", "setterless#en")
        .haveAtMost(2, new Condition<String>() {
          @Override
          public boolean matches(String value) {
            return value.startsWith("setterless#");
          }
        });

    assertThat(node.get("setterless")).isEqualTo(mapper.getNodeFactory().textNode("root"));
    assertThat(node.get("setterless#fr")).isEqualTo(mapper.getNodeFactory().textNode("Français"));
    assertThat(node.get("setterless#en")).isEqualTo(mapper.getNodeFactory().textNode("English"));
  }

  @Test
  public void testSetterlessDeserialization() throws Exception {
    ObjectNode node = new ObjectNode(JsonNodeFactory.instance)
        .put("setterless", "root")
        .put("setterless#fr", "Français")
        .put("setterless#en", "English");

    ObjectMapper mapper = new ObjectMapper()
        .registerModule(new LocalizableModule());

    Foo foo = mapper.convertValue(node, Foo.class);

    assertThat(foo).isNotNull();
    assertThat(foo.getSetterless()).isNotNull();
    assertThat(foo.getSetterless().values).containsOnly(
        entry(ULocale.ROOT, "root"),
        entry(ULocale.FRENCH, "Français"),
        entry(ULocale.ENGLISH, "English")
    );
  }
}
