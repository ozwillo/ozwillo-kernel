package oasis.model.applications;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.annotations.Id;
import oasis.model.i18n.LocalizableString;

@JsonRootName("scope")
public class Scope {
  @Id
  @ApiModelProperty
  private String id;

  @JsonProperty
  @ApiModelProperty
  private LocalizableString title = new LocalizableString();

  @JsonProperty
  @ApiModelProperty
  private LocalizableString description = new LocalizableString();

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
    this.title = new LocalizableString(other.getTitle());
    this.description = new LocalizableString(other.getDescription());
    this.dataProviderId = other.getDataProviderId();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public LocalizableString getTitle() {
    return title.unmodifiable();
  }

  public void setTitle(LocalizableString title) {
    this.title = new LocalizableString(title);
  }

  public LocalizableString getDescription() {
    return description.unmodifiable();
  }

  public void setDescription(LocalizableString description) {
    this.description = new LocalizableString(description);
  }

  public String getDataProviderId() {
    return dataProviderId;
  }

  public void setDataProviderId(String dataProviderId) {
    this.dataProviderId = dataProviderId;
  }
}
