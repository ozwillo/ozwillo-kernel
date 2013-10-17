package oasis.web.authn;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.ws.rs.NameBinding;

/**
 * Indicates that the JAX-RS resource needs an authenticated user.
 * <p>
 * Must be used with {@link Authenticated}.
 *
 * @see UserAuthenticationFilter
 */
@NameBinding
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface User {
}
