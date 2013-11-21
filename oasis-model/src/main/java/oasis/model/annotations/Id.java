package oasis.model.annotations;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonProperty;

@Retention(RUNTIME)
@JacksonAnnotationsInside

@JsonProperty
public @interface Id {
}
