package oasis.model.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;

@JsonRootName("account")
public abstract class Account {

  @JsonProperty
  @ApiModelProperty(required = true)
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
