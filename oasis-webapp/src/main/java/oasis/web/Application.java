package oasis.web;


import com.google.common.collect.ImmutableSet;
import com.wordnik.swagger.jaxrs.listing.ApiDeclarationProvider;
import com.wordnik.swagger.jaxrs.listing.ApiListingResourceJSON;
import com.wordnik.swagger.jaxrs.listing.ResourceListingProvider;
import java.util.Set;
import oasis.web.example.HelloWorld;
import oasis.web.view.HandlebarsBodyWriter;

public class Application extends javax.ws.rs.core.Application {

  @Override
  public Set<Class<?>> getClasses() {
    return ImmutableSet.<Class<?>>of(
            // Providers
            HandlebarsBodyWriter.class,
            ResourceListingProvider.class,
            ApiDeclarationProvider.class,
            //Resources
            Home.class,
            SwaggerUI.class,
            ApiListingResourceJSON.class,
            HelloWorld.class);
  }
}
