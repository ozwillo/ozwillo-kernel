package oasis.model.authn;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Credentials {
  @JsonProperty
  private ClientType clientType;
  @JsonProperty
  private String id;
  @JsonProperty
  private byte[] hash;
  @JsonProperty
  private byte[] salt;

  public ClientType getClientType() {
    return clientType;
  }

  public void setClientType(ClientType clientType) {
    this.clientType = clientType;
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
