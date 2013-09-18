package oasis.web;

import java.util.Collections;
import java.util.Set;

public class Application extends javax.ws.rs.core.Application {

  @Override
  public Set<Class<?>> getClasses() {
    return Collections.<Class<?>>singleton(Home.class);
  }
}
