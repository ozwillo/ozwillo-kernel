package oasis.http;

import javax.inject.Singleton;
import javax.ws.rs.client.Client;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.squareup.okhttp.OkHttpClient;

import oasis.web.providers.JacksonJsonProvider;

public class HttpClientModule extends AbstractModule {

  @Override
  protected void configure() { }

  @Provides @Singleton Client provideClient(OkHttpClient okHttpClient) {
    return new ResteasyClientBuilder()
        .httpEngine(new OkHttpClientEngine(okHttpClient))
        .register(JacksonJsonProvider.class)
        .build();
  }

  @Provides @Singleton OkHttpClient provideOkHttpClient() {
    OkHttpClient client = new OkHttpClient();
    client.setFollowRedirects(false);
    return client;
  }
}
