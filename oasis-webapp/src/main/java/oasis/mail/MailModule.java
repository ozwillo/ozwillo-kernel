package oasis.mail;

import java.util.Properties;

import javax.annotation.Nullable;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.URLName;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.typesafe.config.Config;

public abstract class MailModule extends AbstractModule {
  private static Logger logger = LoggerFactory.getLogger(MailModule.class);

  public static class Settings {

    public static Builder builder() {
      return new Builder();
    }

    public static Settings fromConfig(Config config) {
      if (config.getBoolean("disabled")) {
        return Settings.builder().setEnabled(false).build();
      }

      InternetAddress from;
      try {
        from = new InternetAddress(config.getString("from"));
      } catch (AddressException e) {
        from = InternetAddress.getLocalAddress(null);
      }
      if (from == null) {
        logger.warn("Unable to determine sender address, disabling mail.");
        return Settings.builder().setEnabled(false).build();
      }
      return Settings.builder()
          .setEnabled(true)
          .setFrom(from)
          .setServer(new URLName(config.getString("server")))
          .setUseStartTls(config.getBoolean("starttls.enable"))
          .build();
    }

    public static class Builder {

      private boolean enabled;
      private InternetAddress from;
      private URLName server;
      private boolean useStartTls;

      public Settings build() {
        return new Settings(this);
      }

      public Builder setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
      }

      public Builder setFrom(InternetAddress from) {
        this.from = from;
        return this;
      }

      public Builder setServer(URLName server) {
        this.server = server;
        return this;
      }

      public Builder setUseStartTls(boolean useStartTls) {
        this.useStartTls = useStartTls;
        return this;
      }
    }

    public final boolean enabled;
    public final InternetAddress from;
    public final URLName server;
    public final boolean useStartTls;

    private Settings(Builder builder) {
      this.enabled = builder.enabled;
      this.from = builder.from;
      this.server = builder.server;
      this.useStartTls = builder.useStartTls;
    }
  }

  public static MailModule create(Config config) {
    Settings settings = Settings.fromConfig(config);
    if (settings.enabled) {
      return new EnabledMailModule(settings);
    } else {
      return new DisabledMailModule(settings);
    }
  }

  protected final Settings settings;

  MailModule(Settings settings) {
    this.settings = settings;
  }

  @Override
  protected void configure() {
    bind(Settings.class).toInstance(settings);
  }

  static class DisabledMailModule extends MailModule {
    DisabledMailModule(Settings settings) {
      super(settings);
    }

    @Provides @Nullable MailSender providerMailSender() {
      return null;
    }
  }

  static class EnabledMailModule extends MailModule {
    EnabledMailModule(Settings settings) {
      super(settings);
    }

    @Provides @Singleton Session provideSession() {
      String protocol = settings.server.getProtocol();
      Properties props = new Properties();
      props.setProperty("mail." + protocol + ".starttls.enable", settings.useStartTls ? "true" : "false");
      // TODO: add a require-StartTLS config option
      props.setProperty("mail." + protocol + ".host", settings.server.getHost());
      if (settings.server.getPort() >= 0) {
        props.setProperty("mail." + protocol + ".port", Integer.toString(settings.server.getPort()));
      }
      Authenticator authenticator;
      if (!Strings.isNullOrEmpty(settings.server.getUsername()) && !Strings.isNullOrEmpty(settings.server.getPassword())) {
        props.setProperty("mail." + protocol + ".auth", "true");
        props.setProperty("mail." + protocol + ".user", settings.server.getUsername());
        authenticator = new Authenticator() {
          @Override
          protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(settings.server.getUsername(), settings.server.getPassword());
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
}
