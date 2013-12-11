package oasis.model;

public class InvalidVersionException extends Exception {

  public InvalidVersionException(String type, String id) {
    super("Invalid version for object " + type + ":" + id);
  }

}
