package oasis.model.directory;

import java.net.URI;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.annotations.Id;

@JsonRootName("organization")
public class Organization {

  @Id
  @ApiModelProperty(required = true)
  private String id;

  @JsonProperty
  @ApiModelProperty(required = true)
  private String name;

  @JsonProperty
  @ApiModelProperty(required = true)
  private Type type;
  private URI territory_id;

  public Organization() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public Organization(@Nonnull Organization other) {
    this.name = other.getName();
    this.type = other.getType();
    this.territory_id = other.getTerritory_id();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public URI getTerritory_id() {
    return territory_id;
  }

  public void setTerritory_id(URI territory_id) {
    this.territory_id = territory_id;
  }

  public enum Type {
    PUBLIC_BODY,
    COMPANY
  }
}
