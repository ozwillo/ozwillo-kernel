package oasis.soy;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.SoyModule;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.xliffmsgplugin.XliffMsgPluginModule;

public class SoyGuiceModule extends AbstractModule {
  @Override
  protected void configure() {
    install(new SoyModule());
    install(new XliffMsgPluginModule());
  }

  @Provides @Singleton SoyTofu provideSoyTofu(SoyFileSet.Builder builder) {
    return builder
        .add(getClass().getResource("/templates/Common.soy"))
        .add(getClass().getResource("/templates/Home.soy"))
        .add(getClass().getResource("/templates/SwaggerUI.soy"))
        .add(getClass().getResource("/templates/Login.soy"))
        .add(getClass().getResource("/templates/Logout.soy"))
        .add(getClass().getResource("/templates/Authorize.soy"))
        .add(getClass().getResource("/templates/ChangePassword.soy"))
        .add(getClass().getResource("/templates/KibanaConfig.soy"))
        .add(getClass().getResource("/templates/mail/SignUp.soy"))
        .build()
        .compileToTofu();
  }
}
