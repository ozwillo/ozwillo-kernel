package oasis.soy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import javax.inject.Inject;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.tofu.SoyTofu;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.DisplayContext;
import com.ibm.icu.text.LocaleDisplayNames;
import com.ibm.icu.util.ULocale;

import oasis.web.i18n.LocaleHelper;

public class SoyTemplateRenderer {
  private static final LoadingCache<String, SoyList> SUPPORTED_LOCALES = CacheBuilder.newBuilder()
      .initialCapacity(LocaleHelper.SUPPORTED_LOCALES.size())
      .maximumSize(LocaleHelper.SUPPORTED_LOCALES.size())
      .build(new CacheLoader<String, SoyList>() {
        @Override
        public SoyList load(String key) throws Exception {
          final ULocale currentLocale = ULocale.forLanguageTag(key);
          final LocaleDisplayNames currentLocaleDisplayNames = getLocaleDisplayNames(currentLocale);
          final ArrayList<SoyMapData> list = new ArrayList<>();
          for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
            LocaleDisplayNames localeDisplayNames = getLocaleDisplayNames(locale);
            SoyMapData localeDesc = new SoyMapData(
                "locale", locale.toLanguageTag(),
                "native_name", getDisplayName(locale, localeDisplayNames),
                "translated_name", getDisplayName(locale, currentLocaleDisplayNames)
            );
            list.add(localeDesc);
          }
          // We need a consistent ordering for all locales, so sort on the native_name using a locale-independent collator.
          Collections.sort(list, new Comparator<SoyMapData>() {
            final Collator collator = Collator.getInstance(ULocale.ROOT);
            @Override
            public int compare(SoyMapData o1, SoyMapData o2) {
              return collator.compare(o1.getString("native_name"), o2.getString("native_name"));
            }
          });
          return new SoyListData(list);
        }
      });

  private static final SoyMap LOCALE_NAMES;
  static {
    SoyMapData localeNames = new SoyMapData();
    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      localeNames.put(locale.toLanguageTag(), getDisplayName(locale, getLocaleDisplayNames(locale)));
    }
    LOCALE_NAMES = localeNames;
  }

  private static LocaleDisplayNames getLocaleDisplayNames(ULocale locale) {
    return LocaleDisplayNames.getInstance(locale,
        DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU,
        DisplayContext.DIALECT_NAMES, // XXX: Default is STANDARD_NAMES, should we use that instead?
        DisplayContext.LENGTH_FULL);
  }

  private static String toTitleCase(ULocale locale, String name) {
    // Logic copied from com.ibm.icu.impl.LocaleDisplayNamesImpl#adjustForUsageAndContext
    return UCharacter.toTitleCase(locale, name, BreakIterator.getSentenceInstance(locale),
        UCharacter.TITLECASE_NO_LOWERCASE | UCharacter.TITLECASE_NO_BREAK_ADJUSTMENT);
  }

  // XXX: Use only the language for now, until we add locales for same language in different variants.
  private static String getDisplayName(ULocale locale, LocaleDisplayNames localeDisplayNames) {
    // XXX: explicitly title-case results as some locales (e.g. Bulgarian or Catalan) are all-lowercase even in CAPITALIZATION_FOR_UI_LIST_OR_MENU
    return toTitleCase(localeDisplayNames.getLocale(), localeDisplayNames.languageDisplayName(locale.getLanguage()));
  }

  private final SoyTofu soyTofu;
  private final SoyMsgBundleLoader soyMsgBundleLoader;

  @Inject SoyTemplateRenderer(SoyTofu soyTofu, SoyMsgBundleLoader soyMsgBundleLoader) {
    this.soyTofu = soyTofu;
    this.soyMsgBundleLoader = soyMsgBundleLoader;
  }

  public void render(SoyTemplate template, Appendable writer) {
    SoyMsgBundle msgBundle = soyMsgBundleLoader.get(template.getLocale());
    SoyTofu.Renderer renderer = soyTofu.newRenderer(template.getTemplateInfo())
        .setData(template.getData())
        .setMsgBundle(msgBundle)
        .setIjData(new SoyMapData(
            "current_locale", msgBundle.getLocaleString(),
            "locale_name_map", LOCALE_NAMES,
            "supported_locales", SUPPORTED_LOCALES.getUnchecked(msgBundle.getLocaleString())
        ));
    if (template.getContentKind() != null) {
      renderer.setContentKind(template.getContentKind());
    }
    renderer.render(writer);
  }

  public String renderAsString(SoyTemplate template) {
    StringBuilder sb = new StringBuilder();
    render(template, sb);
    return sb.toString();
  }
}
