package oasis.web;

import com.google.common.collect.ImmutableSet;
import oasis.web.providers.HandlebarsBodyWriter;

import java.util.Set;

public class Application extends javax.ws.rs.core.Application {

  @Override
  public Set<Class<?>> getClasses() {
    return ImmutableSet.<Class<?>>of(
            // Providers
            HandlebarsBodyWriter.class,
            //Resources
            Home.class);
  }
}
