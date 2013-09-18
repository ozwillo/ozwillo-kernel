package oasis.model.example;

import com.wordnik.swagger.annotations.ApiModelProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "greeting")
public class Greeting {
  @XmlElement(name = "name")
  @ApiModelProperty(required = true, notes = "name")
  private String name;

  @XmlElement(name = "lastname")
  @ApiModelProperty(required = false, notes = "lastname")
  private String lastname;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLastname() {
    return lastname;
  }

  public void setLastname(String lastname) {
    this.lastname = lastname;
  }
}
