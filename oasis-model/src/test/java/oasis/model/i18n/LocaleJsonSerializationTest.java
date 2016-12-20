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
package oasis.model.i18n;

import static org.assertj.core.api.Assertions.*;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.icu.util.ULocale;

public class LocaleJsonSerializationTest {
  public static class Foo {
    @JsonProperty ULocale locale;
  }

  @Test public void testSerialization() throws Exception {
    Foo foo = new Foo();
    foo.locale = ULocale.FRANCE;

    ObjectMapper mapper = new ObjectMapper()
        .registerModule(new LocalizableModule());
    ObjectNode node = mapper.convertValue(foo, ObjectNode.class);

    assertThat(node.get("locale"))
        .describedAs(mapper.writeValueAsString(foo))
        .isEqualTo(mapper.getNodeFactory().textNode(ULocale.FRANCE.toLanguageTag()));
  }

  @Test public void testDeserialization() throws Exception {
    ObjectMapper mapper = new ObjectMapper()
        .registerModule(new LocalizableModule());
    Foo foo = mapper.readValue("{\"locale\": \"en-GB\"}", Foo.class);

    assertThat(foo.locale).isEqualTo(ULocale.UK);
  }
}
