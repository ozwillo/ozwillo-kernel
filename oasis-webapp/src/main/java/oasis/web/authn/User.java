package oasis.web.authn;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.ws.rs.NameBinding;

/**
 * Indicates that the JAX-RS resource wants to know the authenticated user (if there's one).
 * <p>
 * Can be used with {@link Authenticated} to mandate an authenticated user.
 *
 * @see UserFilter
 * @see UserAuthenticationFilter
 */
@NameBinding
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface User {
}
