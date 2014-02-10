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
