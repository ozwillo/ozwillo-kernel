/**
 * Ozwillo Kernel
 * Copyright (C) 2015  Atol Conseils & Développements
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.wordnik.swagger.config.ConfigFactory;
import com.wordnik.swagger.config.ScannerFactory;
import com.wordnik.swagger.jaxrs.config.DefaultJaxrsScanner;
import com.wordnik.swagger.jaxrs.reader.DefaultJaxrsApiReader;
import com.wordnik.swagger.reader.ClassReaders;

import oasis.auditlog.log4j.Log4JAuditLogModule;
import oasis.auditlog.noop.NoopAuditLogModule;
import oasis.auth.AuthModule;
import oasis.catalog.CatalogModule;
import oasis.elasticsearch.ElasticsearchModule;
import oasis.http.HttpClientModule;
import oasis.http.HttpServer;
import oasis.http.HttpServerModule;
import oasis.jest.JestService;
import oasis.jest.guice.JestModule;
import oasis.jongo.JongoService;
import oasis.jongo.guice.JongoModule;
import oasis.mail.MailModule;
import oasis.soy.SoyGuiceModule;
import oasis.tools.CommandLineTool;
import oasis.urls.UrlsModule;
import oasis.userdirectory.UserDirectoryModule;
import oasis.web.guice.OasisGuiceModule;

public class WebApp extends CommandLineTool {
  // logger is not a static field to be initialized once log4j is configured
  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(WebApp.class);
  }

  public void run(String[] args) throws Throwable {
    final Config config = init(args);

    AbstractModule auditModule = (config.getBoolean("oasis.auditlog.disabled")) ?
        new NoopAuditLogModule() :
        new Log4JAuditLogModule();

    final Injector injector = Guice.createInjector(
        new OasisGuiceModule(),
        JongoModule.create(config.getConfig("oasis.mongo")),
        auditModule,
        new HttpClientModule(),
        HttpServerModule.create(config.getConfig("oasis.http")),
        ElasticsearchModule.create(config.getConfig("oasis.elasticsearch")),
        new JestModule(),
        new SoyGuiceModule(),
        new CatalogModule(),
        // TODO: store PKIs in DB to use a single subtree of the config
        AuthModule.create(config.getConfig("oasis.auth")
            .withFallback(config.withOnlyPath("oasis.conf-dir"))),
        UrlsModule.create(config.getConfig("oasis.urls")),
        MailModule.create(config.getConfig("oasis.mail")),
        UserDirectoryModule.create(config.getConfig("oasis.userdirectory"))
    );

    final HttpServer server = injector.getInstance(HttpServer.class);
    final JongoService jongo = injector.getInstance(JongoService.class);
    final JestService jest = injector.getInstance(JestService.class);

    initSwagger(config);

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        server.stop();
        jest.stop();
        jongo.stop();
      }
    });

    jongo.start();
    jest.start();
    server.start();
  }

  private static void initSwagger(Config config) {
    ConfigFactory.config().setApiVersion(config.getString("swagger.api.version"));

    // TODO: authorizations and info
    ScannerFactory.setScanner(new DefaultJaxrsScanner());
    ClassReaders.setReader(new DefaultJaxrsApiReader());
  }

  public static void main(String[] args) throws Throwable {
    new WebApp().run(args);
  }
}
