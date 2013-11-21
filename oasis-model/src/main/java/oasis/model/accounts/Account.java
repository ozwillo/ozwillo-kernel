package oasis.model.accounts;

import com.fasterxml.jackson.annotation.JsonRootName;
import com.wordnik.swagger.annotations.ApiModelProperty;
import oasis.model.annotations.Id;

@JsonRootName("account")
public abstract class Account {

  @ApiModelProperty(required = true)
  @Id
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}
