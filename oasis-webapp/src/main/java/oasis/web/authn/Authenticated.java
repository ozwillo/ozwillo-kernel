package oasis.web.authn;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.ws.rs.NameBinding;

/**
 * Indicates that the JAX-RS resource needs authentication.
 * <p>
 * Must be combined with one of {@link Client}, {@link OAuth} or {@link User} to select the kind of authentication
 * needed.
 */
@NameBinding
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Authenticated {
}
