package oasis.jongo.etag;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface HasModified {
  @JsonProperty
  long getModified();
}
