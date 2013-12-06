package oasis.model.applications;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.annotations.Id;

@JsonRootName("scope")
public class Scope {
  @Id
  @ApiModelProperty
  private String id;

  // TODO: Manage I18N
  @JsonProperty
  @ApiModelProperty
  private String title;

  // TODO: Manage I18N
  @JsonProperty
  @ApiModelProperty
  private String description;

  @JsonProperty
  @ApiModelProperty
  private String dataProviderId;

  public Scope() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public Scope(@Nonnull Scope other) {
    this.title = other.getTitle();
    this.description = other.getDescription();
    this.dataProviderId = other.getDataProviderId();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getDataProviderId() {
    return dataProviderId;
  }

  public void setDataProviderId(String dataProviderId) {
    this.dataProviderId = dataProviderId;
  }
}
