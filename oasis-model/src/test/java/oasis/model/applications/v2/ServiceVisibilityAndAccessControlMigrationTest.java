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
package oasis.model.applications.v2;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.*;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SuppressWarnings("deprecation")
public class ServiceVisibilityAndAccessControlMigrationTest {

  ObjectMapper objectMapper = new ObjectMapper()
      .disable(FAIL_ON_UNKNOWN_PROPERTIES)
      .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

  @Test public void testApplicationBackwardsCompatibility() {
    Application application = new Application();
    assumeFalse(application.isVisible());
    ObjectNode node = objectMapper.convertValue(application, ObjectNode.class);
    assertAbsentOrFalse(node.get("visible"));

    application.setVisible(true);
    node = objectMapper.convertValue(application, ObjectNode.class);
    assertPresentAndTrue(node.get("visible"));

    node = new ObjectNode(JsonNodeFactory.instance);
    assumeFalse(node.has("visible"));
    application = objectMapper.convertValue(node, Application.class);
    assertThat(application.isVisible()).isFalse();

    node.put("visible", false);
    application = objectMapper.convertValue(node, Application.class);
    assertThat(application.isVisible()).isFalse();

    node.put("visible", true);
    application = objectMapper.convertValue(node, Application.class);
    assertThat(application.isVisible()).isTrue();
  }

  @Test public void testSimpleCatalogEntryBackwardsCompatibility() {
    SimpleCatalogEntry entry = new SimpleCatalogEntry();
    assumeFalse(entry.isVisible());
    ObjectNode node = objectMapper.convertValue(entry, ObjectNode.class);
    assertAbsentOrFalse(node.get("visible"));

    entry.setVisible(true);
    node = objectMapper.convertValue(entry, ObjectNode.class);
    assertPresentAndTrue(node.get("visible"));

    node = new ObjectNode(JsonNodeFactory.instance);
    assumeFalse(node.has("visible"));
    entry = objectMapper.convertValue(node, SimpleCatalogEntry.class);
    assertThat(entry.isVisible()).isFalse();

    node.put("visible", false);
    entry = objectMapper.convertValue(node, SimpleCatalogEntry.class);
    assertThat(entry.isVisible()).isFalse();

    node.put("visible", true);
    entry = objectMapper.convertValue(node, SimpleCatalogEntry.class);
    assertThat(entry.isVisible()).isTrue();
  }

  @Test public void testServiceSerialization() {
    Service service = new Service();
    assumeFalse(service.isVisible());
    assumeFalse(Boolean.TRUE.equals(service.getRestricted()));
    assumeTrue(service.isAccessRestricted());
    assumeTrue(service.getVisibility() == Service.Visibility.HIDDEN);
    assumeTrue(service.getAccess_control() == Service.AccessControl.RESTRICTED);
    ObjectNode node = objectMapper.convertValue(service, ObjectNode.class);
    assertAbsentOrFalse(node.get("visible"));
    assertAbsentOrFalse(node.get("restricted"));
    assertThat(node.get("visibility").textValue()).isEqualTo(Service.Visibility.HIDDEN.name());
    assertThat(node.get("access_control").textValue()).isEqualTo(Service.AccessControl.RESTRICTED.name());

    service.setVisible(true);
    service.setRestricted(false);
    node = objectMapper.convertValue(service, ObjectNode.class);
    assertPresentAndTrue(node.get("visible"));
    assertAbsentOrFalse(node.get("restricted"));
    assertThat(node.get("visibility").textValue()).isEqualTo(Service.Visibility.VISIBLE.name());
    assertThat(node.get("access_control").textValue()).isEqualTo(Service.AccessControl.ANYONE.name());

    service.setVisible(true);
    service.setRestricted(true);
    assumeFalse(service.isVisible()); // restricted overrides visible
    node = objectMapper.convertValue(service, ObjectNode.class);
    assertAbsentOrFalse(node.get("visible"));
    assertPresentAndTrue(node.get("restricted"));
    assertThat(node.get("visibility").textValue()).isEqualTo(Service.Visibility.NEVER_VISIBLE.name());
    assertThat(node.get("access_control").textValue()).isEqualTo(Service.AccessControl.ALWAYS_RESTRICTED.name());

    service.setVisible(false);
    service.setRestricted(true);
    node = objectMapper.convertValue(service, ObjectNode.class);
    assertAbsentOrFalse(node.get("visible"));
    assertPresentAndTrue(node.get("restricted"));
    assertThat(node.get("visibility").textValue()).isEqualTo(Service.Visibility.NEVER_VISIBLE.name());
    assertThat(node.get("access_control").textValue()).isEqualTo(Service.AccessControl.ALWAYS_RESTRICTED.name());
  }

  @Test public void testServiceDeserialization() {
    ObjectNode node = new ObjectNode(JsonNodeFactory.instance);
    assumeFalse(node.has("visible"));
    assumeFalse(node.has("visibility"));
    assumeFalse(node.has("restricted"));
    assumeFalse(node.has("access_control"));
    Service service = objectMapper.convertValue(node, Service.class);
    assertThat(service.isVisible()).isFalse();
    assertThat(service.getRestricted()).isNotEqualTo(Boolean.TRUE);
    assertThat(service.isAccessRestricted()).isTrue();
    assertThat(service.getVisibility()).isEqualTo(Service.Visibility.HIDDEN);
    assertThat(service.getAccess_control()).isEqualTo(Service.AccessControl.RESTRICTED);

    node.put("visible", true);
    node.put("restricted", false);
    service = objectMapper.convertValue(node, Service.class);
    assertThat(service.isVisible()).isTrue();
    assertThat(service.getRestricted()).isNotEqualTo(Boolean.TRUE);
    assertThat(service.isAccessRestricted()).isFalse();
    assertThat(service.getVisibility()).isEqualTo(Service.Visibility.VISIBLE);
    assertThat(service.getAccess_control()).isEqualTo(Service.AccessControl.ANYONE);

    node.put("visible", true);
    node.put("restricted", true);
    service = objectMapper.convertValue(node, Service.class);
    assertThat(service.isVisible()).isFalse(); // restricted overrides visible
    assertThat(service.getRestricted()).isEqualTo(Boolean.TRUE);
    assertThat(service.isAccessRestricted()).isTrue();
    assertThat(service.getVisibility()).isEqualTo(Service.Visibility.NEVER_VISIBLE);
    assertThat(service.getAccess_control()).isEqualTo(Service.AccessControl.ALWAYS_RESTRICTED);

    node.put("visible", false);
    node.put("restricted", true);
    service = objectMapper.convertValue(node, Service.class);
    assertThat(service.isVisible()).isFalse();
    assertThat(service.getRestricted()).isEqualTo(Boolean.TRUE);
    assertThat(service.isAccessRestricted()).isTrue();
    assertThat(service.getVisibility()).isEqualTo(Service.Visibility.NEVER_VISIBLE);
    assertThat(service.getAccess_control()).isEqualTo(Service.AccessControl.ALWAYS_RESTRICTED);
  }

  @Test public void testNewPropertiesAreNotIgnored() {
    ObjectNode node = new ObjectNode(JsonNodeFactory.instance);
    node.put("visibility", "whatever");
    try {
      objectMapper.convertValue(node, Service.class);
    } catch (IllegalArgumentException iae) {
      assertThat(iae)
          .hasCauseInstanceOf(InvalidFormatException.class)
          .hasMessageContaining("whatever")
          .hasMessageContaining(Service.Visibility.class.getName());
    }

    node = new ObjectNode(JsonNodeFactory.instance);
    node.put("access_control", "nevermind");
    try {
      objectMapper.convertValue(node, Service.class);
    } catch (IllegalArgumentException iae) {
      assertThat(iae)
          .hasCauseInstanceOf(InvalidFormatException.class)
          .hasMessageContaining("nevermind")
          .hasMessageContaining(Service.AccessControl.class.getName());
    }
  }

  private void assertAbsentOrFalse(JsonNode prop) {
    if (prop != null) {
      assertThat(prop.isBoolean()).isTrue();
      assertThat(prop.booleanValue()).isFalse();
    }
  }

  private void assertPresentAndTrue(JsonNode prop) {
    assertThat(prop).isNotNull();
    assertThat(prop.booleanValue()).isTrue();
  }
}
