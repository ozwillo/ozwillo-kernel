/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
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
package oasis.mail;

import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.URLName;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.typesafe.config.Config;

@Value.Enclosing
public class MailModule extends AbstractModule {
  private static Logger logger = LoggerFactory.getLogger(MailModule.class);

  @Value.Immutable
  public interface Settings {

    InternetAddress from_();

    URLName server();

    boolean useStartTls();

    static Settings fromConfig(Config config) {
      InternetAddress from;
      if (config.hasPath("from")) {
        try {
          from = new InternetAddress(config.getString("from"));
        } catch (AddressException e) {
          from = null;
        }
      } else {
        logger.warn("No configured sender address, defaulting to the current user (as of javax.mail.internet.InternetAddress.getLocalAddress)");
        from = InternetAddress.getLocalAddress(null);
      }
      if (from == null) {
        throw new RuntimeException("Unable to determine sender address.");
      }
      return ImmutableMailModule.Settings.builder()
          .from_(from)
          .server(new URLName(config.getString("server")))
          .useStartTls(config.getBoolean("starttls.enable"))
          .build();
    }
  }

  public static MailModule create(Config config) {
    Settings settings = Settings.fromConfig(config);
    return new MailModule(settings);
  }



  protected final Settings settings;

  MailModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
  }

  @Provides @Singleton Session provideSession() {
    String protocol = settings.server().getProtocol();
    Properties props = new Properties();
    props.setProperty("mail." + protocol + ".starttls.enable", settings.useStartTls() ? "true" : "false");
    // TODO: add a require-StartTLS config option
    props.setProperty("mail." + protocol + ".host", settings.server().getHost());
    if (settings.server().getPort() >= 0) {
      props.setProperty("mail." + protocol + ".port", Integer.toString(settings.server().getPort()));
    }
    Authenticator authenticator;
    if (!Strings.isNullOrEmpty(settings.server().getUsername()) && !Strings.isNullOrEmpty(settings.server().getPassword())) {
      props.setProperty("mail." + protocol + ".auth", "true");
      props.setProperty("mail." + protocol + ".user", settings.server().getUsername());
      authenticator = new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
          return new PasswordAuthentication(settings.server().getUsername(), settings.server().getPassword());
        }
      };
    } else {
      authenticator = null;
    }
    Session session = Session.getInstance(props, authenticator);
    session.setProtocolForAddress("rfc822", protocol);
    return session;
  }
}
