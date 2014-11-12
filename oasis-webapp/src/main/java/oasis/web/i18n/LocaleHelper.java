package oasis.web.i18n;

import java.util.Locale;

import javax.annotation.Nullable;

public class LocaleHelper {
  @Nullable
  public String selectLocale(Iterable<String> ui_locales) {
    return null;
  }

  public Locale getLocale(@Nullable Locale locale) {
    return locale == null ? Locale.UK : locale;
  }
}
