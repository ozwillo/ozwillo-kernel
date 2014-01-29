package oasis.web.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.ws.rs.NameBinding;

/**
 * Indicates to a JAX-RS resource that only same-site requests should be allowed to access it.
 * <p>
 * Should be used for all non-GET, non-HEAD requests made by a browser.
 *
 * @see <a href="http://seclab.stanford.edu/websec/csrf/csrf.pdf">http://seclab.stanford.edu/websec/csrf/csrf.pdf</a>
 * @see StrictRefererFilter
 */
@NameBinding
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface StrictReferer {
}
