package oasis.web.authn;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.ws.rs.NameBinding;

/**
 * Indicates that the resource needs special scopes.
 * <p>
 * Must be used with {@link Authenticated} and {@link OAuth}.
 */
@NameBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface WithScopes {
  String[] value();
}
