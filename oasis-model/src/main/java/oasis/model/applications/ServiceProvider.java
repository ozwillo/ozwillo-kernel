package oasis.model.applications;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

import oasis.model.annotations.Id;
import oasis.model.i18n.LocalizableString;

@Deprecated
@JsonRootName("serviceProvider")
public class ServiceProvider {
  @Id
  @ApiModelProperty(required = true)
  private String id;

  @JsonProperty
  @ApiModelProperty(required = true)
  private LocalizableString name = new LocalizableString();

  @JsonProperty
  @ApiModelProperty
  private List<ScopeCardinality> scopeCardinalities = new ArrayList<>();

  @JsonProperty
  @ApiModelProperty
  private List<String> redirect_uris = new ArrayList<>();

  @JsonProperty
  @ApiModelProperty
  private List<String> post_logout_redirect_uris = new ArrayList<>();

  public ServiceProvider() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public ServiceProvider(ServiceProvider other) {
    this.name = new LocalizableString(other.getName());
    this.scopeCardinalities = new ArrayList<>(other.getScopeCardinalities());
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public LocalizableString getName() {
    return name.unmodifiable();
  }

  public void setName(LocalizableString name) {
    this.name = new LocalizableString(name);
  }

  public List<ScopeCardinality> getScopeCardinalities() {
    return Collections.unmodifiableList(scopeCardinalities);
  }

  public void setScopeCardinalities(List<ScopeCardinality> scopeCardinalities) {
    this.scopeCardinalities = new ArrayList<>(scopeCardinalities);
  }

  public List<String> getRedirect_uris() {
    return Collections.unmodifiableList(redirect_uris);
  }

  public void setRedirect_uris(List<String> redirect_uris) {
    this.redirect_uris = new ArrayList<>(redirect_uris);
  }

  public List<String> getPost_logout_redirect_uris() {
    return Collections.unmodifiableList(post_logout_redirect_uris);
  }

  public void setPost_logout_redirect_uris(List<String> post_logout_redirect_uris) {
    this.post_logout_redirect_uris = new ArrayList<>(post_logout_redirect_uris);
  }
}
