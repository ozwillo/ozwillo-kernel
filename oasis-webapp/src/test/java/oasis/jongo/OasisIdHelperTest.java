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
package oasis.jongo;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

import java.lang.reflect.Field;

import org.junit.Test;

import oasis.model.annotations.Id;

public class OasisIdHelperTest {
  @Test
  public void testFindIdFieldOfClassWithId() {
    Field field = OasisIdHelper.findOasisIdField(ClassWithId.class);
    assertNotNull(field);
  }

  @Test
  public void testUpdateIdFieldOfClassWithId() {
    ClassWithId classWithId = new ClassWithId();
    assumeTrue(classWithId.id == null);
    OasisIdHelper.updateOasisIdField(classWithId);
    assertNotNull(classWithId.id);
  }

  @Test
  public void testFindIdFieldOfClassWithInheritedId() {
    Field field = OasisIdHelper.findOasisIdField(ClassWithInheritedId.class);
    assertNotNull(field);
  }

  @Test
  public void testUpdateIdFieldOfClassWithInheritedId() {
    ClassWithInheritedId classWithInheritedId = new ClassWithInheritedId();
    assumeTrue(classWithInheritedId.id == null);
    OasisIdHelper.updateOasisIdField(classWithInheritedId);
    assertNotNull(classWithInheritedId.id);
  }

  @Test
  public void testFindIdFieldOfClassWithLongId() {
    Field field = OasisIdHelper.findOasisIdField(ClassWithLongId.class);
    assertNotNull(field);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUpdateIdFieldOfClassWithLongId() {
    ClassWithLongId classWithLongId = new ClassWithLongId();
    assumeTrue(classWithLongId.id == null);
    // Should throw
    OasisIdHelper.updateOasisIdField(classWithLongId);
  }

  @Test
  public void testFindIdFieldOfClassWithoutId() {
    Field field = OasisIdHelper.findOasisIdField(ClassWithoutId.class);
    assertNull(field);
  }

  @Test
  public void testUpdateIdFieldOfClassWithoutId() {
    ClassWithoutId classWithoutId = new ClassWithoutId();
    // Should not throw
    OasisIdHelper.updateOasisIdField(classWithoutId);
  }

  static class ClassWithId {
    @Id String id;
  }

  static class ClassWithInheritedId extends ClassWithId {
  }

  static class ClassWithLongId {
    @Id Long id;
  }

  static class ClassWithoutId {
  }
}
