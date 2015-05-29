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
package oasis.model.applications.v2;

import static com.fasterxml.jackson.databind.DeserializationFeature.*;
import static oasis.model.applications.v2.Service.AccessControl.*;
import static oasis.model.applications.v2.Service.Visibility.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assume.*;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import oasis.model.applications.v2.Service.AccessControl;
import oasis.model.applications.v2.Service.Visibility;

@SuppressWarnings("deprecation")
@RunWith(Parameterized.class)
public class ServiceVisibilityAndAccessControlMigration2Test {

  @Parameters(name = "{index}: visible:{0}, restricted:{1}, visibility:{2}, access_control:{3}; expected: visibility:{4}, access_control:{5}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
        // visible, restricted, visibility,    access_control,    expected visibility, expected access_control
        {  null,    null,       null,          null,              HIDDEN,              RESTRICTED        },
        {  false,   false,      null,          null,              HIDDEN,              RESTRICTED        },
        {  true,    false,      null,          null,              VISIBLE,             ANYONE            },
        {  false,   true,       null,          null,              NEVER_VISIBLE,       ALWAYS_RESTRICTED },
        {  true,    true,       null,          null,              NEVER_VISIBLE,       ALWAYS_RESTRICTED },
        {  null,    null,       HIDDEN,        null,              HIDDEN,              RESTRICTED        },
        {  null,    null,       NEVER_VISIBLE, null,              NEVER_VISIBLE,       RESTRICTED        },
        {  null,    null,       VISIBLE,       null,              VISIBLE,             RESTRICTED        },
        {  null,    null,       null,          RESTRICTED,        HIDDEN,              RESTRICTED        },
        {  null,    null,       null,          ANYONE,            HIDDEN,              ANYONE            },
        {  null,    null,       null,          ALWAYS_RESTRICTED, HIDDEN,              ALWAYS_RESTRICTED },
        {  null,    null,       HIDDEN,        RESTRICTED,        HIDDEN,              RESTRICTED        },
        {  null,    null,       HIDDEN,        ANYONE,            HIDDEN,              ANYONE            },
        {  null,    null,       HIDDEN,        ALWAYS_RESTRICTED, HIDDEN,              ALWAYS_RESTRICTED },
        {  null,    null,       NEVER_VISIBLE, RESTRICTED,        NEVER_VISIBLE,       RESTRICTED        },
        {  null,    null,       NEVER_VISIBLE, ANYONE,            NEVER_VISIBLE,       ANYONE            },
        {  null,    null,       NEVER_VISIBLE, ALWAYS_RESTRICTED, NEVER_VISIBLE,       ALWAYS_RESTRICTED },
        {  null,    null,       VISIBLE,       RESTRICTED,        VISIBLE,             RESTRICTED        },
        {  null,    null,       VISIBLE,       ANYONE,            VISIBLE,             ANYONE            },
        {  null,    null,       VISIBLE,       ALWAYS_RESTRICTED, VISIBLE,             ALWAYS_RESTRICTED },
        // test with incompatible "new" values; "old" properties override "new" ones.
        {  false,   false,      VISIBLE,       ANYONE,            HIDDEN,              RESTRICTED        },
        {  true,    false,      HIDDEN,        RESTRICTED,        VISIBLE,             ANYONE            },
        {  false,   true,       VISIBLE,       ANYONE,            NEVER_VISIBLE,       ALWAYS_RESTRICTED },
        {  true,    true,       HIDDEN,        ANYONE,            NEVER_VISIBLE,       ALWAYS_RESTRICTED },
    });
  }

  ObjectMapper objectMapper = new ObjectMapper()
      .disable(FAIL_ON_UNKNOWN_PROPERTIES)
      .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

  @Parameter(0) public Boolean visible;
  @Parameter(1) public Boolean restricted;
  @Parameter(2) public Visibility visibility;
  @Parameter(3) public AccessControl accessControl;
  @Parameter(4) public Visibility expectedVisibility;
  @Parameter(5) public AccessControl expectedAccessControl;

  @Test public void testServiceProperties() {
    Service service = new Service();
    service.setVisible(visible);
    service.setRestricted(restricted);
    service.setVisibility(visibility);
    service.setAccess_control(accessControl);
    assertServiceExpectedState(service);
  }

  @Test public void testServiceDeserialisation() {
    ObjectNode node = new ObjectNode(JsonNodeFactory.instance);
    if (visible != null) {
      node.put("visible", visible.booleanValue());
    }
    if (restricted != null) {
      node.put("restricted", restricted.booleanValue());
    }
    if (visibility != null) {
      node.put("visibility", visibility.name());
    }
    if (accessControl != null) {
      node.put("access_control", accessControl.name());
    }
    Service service = objectMapper.convertValue(node, Service.class);
    assertServiceExpectedState(service);
  }

  private void assertServiceExpectedState(Service service) {
    assertThat(service.getVisibility()).isEqualTo(expectedVisibility);
    assertThat(service.getAccess_control()).isEqualTo(expectedAccessControl);
    switch (expectedVisibility) {
      case VISIBLE:
        assertThat(service.isVisible()).isTrue();
        break;
      case HIDDEN:
      case NEVER_VISIBLE:
        assertThat(service.isVisible()).isFalse();
        break;
      default:
        throw new AssertionError();
    }
    switch (expectedAccessControl) {
      case ANYONE:
        assertThat(service.isAccessRestricted()).isFalse();
        break;
      case RESTRICTED:
      case ALWAYS_RESTRICTED:
        assertThat(service.isAccessRestricted()).isTrue();
        break;
      default:
        throw new AssertionError();
    }
    if (expectedVisibility == VISIBLE && expectedAccessControl == ANYONE) {
      assertThat(service.getVisible()).isTrue();
      assertThat(service.getRestricted()).isNotEqualTo(Boolean.TRUE); // null or false
    } else if (expectedVisibility == HIDDEN && expectedAccessControl == RESTRICTED) {
      assertThat(service.getVisible()).isNotEqualTo(Boolean.TRUE); // null or false
      assertThat(service.getRestricted()).isNotEqualTo(Boolean.TRUE); // null or false
    } else if (expectedVisibility == NEVER_VISIBLE && expectedAccessControl == ALWAYS_RESTRICTED) {
      assertThat(service.getVisible()).isNotEqualTo(Boolean.TRUE); // null or false
      assertThat(service.getRestricted()).isTrue();
    } else {
      // state cannot be mapped to old properties, they must both be null.
      assertThat(service.getVisible()).isNull();
      assertThat(service.getRestricted()).isNull();
    }
  }

  @Test public void testServiceSerialization() {
    Service service = new Service();
    service.setVisible(visible);
    service.setRestricted(restricted);
    service.setVisibility(visibility);
    service.setAccess_control(accessControl);
    ObjectNode node = objectMapper.convertValue(service, ObjectNode.class);
    assertEnumValue(node, "visibility", expectedVisibility);
    assertEnumValue(node, "access_control", expectedAccessControl);
    if (expectedVisibility == VISIBLE && expectedAccessControl == ANYONE) {
      assertPresentAndTrue(node, "visible");
      assertAbsentOrFalse(node, "restricted");
    } else if (expectedVisibility == HIDDEN && expectedAccessControl == RESTRICTED) {
      assertAbsentOrFalse(node, "visible");
      assertAbsentOrFalse(node, "restricted");
    } else if (expectedVisibility == NEVER_VISIBLE && expectedAccessControl == ALWAYS_RESTRICTED) {
      assertAbsentOrFalse(node, "visible");
      assertPresentAndTrue(node, "restricted");
    } else {
      // state cannot be mapped to old properties, they must both be null.
      assertThat(node.has("visible")).isFalse();
      assertThat(node.has("restricted")).isFalse();
    }
  }

  private void assertEnumValue(ObjectNode node, String fieldName, Enum<?> expectedEnumValue) {
    assertThat(node.has(fieldName)).isTrue();
    assertThat(node.get(fieldName).isTextual()).isTrue();
    assertThat(node.get(fieldName).textValue()).isEqualTo(expectedEnumValue.name());
  }

  private void assertPresentAndTrue(ObjectNode node, String fieldName) {
    assertThat(node.has(fieldName));
    assertThat(node.get(fieldName).isBoolean()).isTrue();
    assertThat(node.get(fieldName).booleanValue()).isTrue();
  }

  private void assertAbsentOrFalse(ObjectNode node, String fieldName) {
    if (node.has(fieldName)) {
      assertThat(node.get(fieldName).isBoolean()).isTrue();
      assertThat(node.get(fieldName).booleanValue()).isFalse();
    }
  }
}
