package oasis.web.i18n;

import java.util.Locale;
import java.util.ResourceBundle;

import javax.annotation.Nullable;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Variant;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import oasis.model.accounts.UserAccount;
import oasis.model.i18n.LocalizableValue;

public class LocaleHelper {
  private static final Locale DEFAULT_LOCALE = Locale.UK;
  private static final ImmutableList<Locale> SUPPORTED_LOCALES = ImmutableList.of(
      Locale.UK
  );
  static {
    assert SUPPORTED_LOCALES.contains(DEFAULT_LOCALE);
  }

  private static final LocalizableValue<Locale> SUPPORTED_LOCALES_MAP;
  static {
    final ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);

    final LocalizableValue<Locale> supportedLocalesMap = new LocalizableValue<>();

    // Iterate in reverse order so that the first locales take precedence (overwrite)
    for (Locale supportedLocale : SUPPORTED_LOCALES.reverse()) {
      for (Locale candidateLocale : control.getCandidateLocales("", supportedLocale)) {
        if (!candidateLocale.equals(Locale.ROOT)) {
          supportedLocalesMap.set(candidateLocale, supportedLocale);
        }
      }
    }

    SUPPORTED_LOCALES_MAP = supportedLocalesMap.unmodifiable();
  }

  private static final ImmutableList<Variant> SUPPORTED_LOCALES_VARIANTS;
  static {
    ImmutableList.Builder<Variant> supportedLocalesVariants = ImmutableList.builder();

    for (Locale supportedLocale : SUPPORTED_LOCALES) {
      supportedLocalesVariants.add(new Variant(null, supportedLocale, null));
    }

    SUPPORTED_LOCALES_VARIANTS = supportedLocalesVariants.build();
  }

  public Locale selectLocale(Iterable<String> ui_locales, Request request) {
    for (String candidateLocale : ui_locales) {
      Locale selectedLocale = SUPPORTED_LOCALES_MAP.get(Locale.forLanguageTag(candidateLocale));
      if (selectedLocale != null) {
        return selectedLocale;
      }
    }
    return selectLocale(request);
  }

  public Locale selectLocale(Request request) {
    Variant selectedVariant = request.selectVariant(SUPPORTED_LOCALES_VARIANTS);
    if (selectedVariant != null) {
      return selectedVariant.getLanguage();
    }
    return DEFAULT_LOCALE;
  }

  public Locale selectLocale(@Nullable Locale locale, Request request) {
    if (locale != null) {
      locale = SUPPORTED_LOCALES_MAP.get(locale);
    }
    if (locale == null) {
      locale = selectLocale(request);
    }
    return MoreObjects.firstNonNull(locale, DEFAULT_LOCALE);
  }
}
