package oasis.model.authn;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Credentials {
  @JsonProperty
  private ClientType type;
  @JsonProperty
  private String id;
  @JsonProperty
  private byte[] hash;
  @JsonProperty
  private byte[] salt;

  public ClientType getType() {
    return type;
  }

  public void setType(ClientType type) {
    this.type = type;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public byte[] getHash() {
    return hash;
  }

  public void setHash(byte[] hash) {
    this.hash = hash;
  }

  public byte[] getSalt() {
    return salt;
  }

  public void setSalt(byte[] salt) {
    this.salt = salt;
  }
}
