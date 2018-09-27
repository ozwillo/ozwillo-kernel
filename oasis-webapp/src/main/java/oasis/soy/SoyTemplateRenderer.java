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
package oasis.soy;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;

import javax.inject.Inject;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.tofu.SoyTofu;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.DisplayContext;
import com.ibm.icu.text.LocaleDisplayNames;
import com.ibm.icu.util.ULocale;

import oasis.model.branding.BrandInfo;
import oasis.services.branding.BrandHelper;
import oasis.urls.BaseUrls;
import oasis.web.i18n.LocaleHelper;

public class SoyTemplateRenderer {
  private static final LoadingCache<String, ImmutableList<ImmutableMap<String, String>>> SUPPORTED_LOCALES = CacheBuilder.newBuilder()
      .initialCapacity(LocaleHelper.SUPPORTED_LOCALES.size())
      .maximumSize(LocaleHelper.SUPPORTED_LOCALES.size())
      .build(new CacheLoader<String, ImmutableList<ImmutableMap<String, String>>>() {
        @Override
        public ImmutableList<ImmutableMap<String, String>> load(String key) {
          final ULocale currentLocale = ULocale.forLanguageTag(key);
          final LocaleDisplayNames currentLocaleDisplayNames = getLocaleDisplayNames(currentLocale);
          final ArrayList<ImmutableMap<String, String>> list = new ArrayList<>();
          for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
            LocaleDisplayNames localeDisplayNames = getLocaleDisplayNames(locale);
            ImmutableMap<String, String>localeDesc = ImmutableMap.of(
                "locale", locale.toLanguageTag(),
                "native_name", getDisplayName(locale, localeDisplayNames),
                "translated_name", getDisplayName(locale, currentLocaleDisplayNames)
            );
            list.add(localeDesc);
          }
          // We need a consistent ordering for all locales, so sort on the native_name using a locale-independent collator.
          list.sort(new Comparator<ImmutableMap<String, String>>() {
            final Collator collator = Collator.getInstance(ULocale.ROOT);
            @Override
            public int compare(ImmutableMap<String, String> o1, ImmutableMap<String, String> o2) {
              return collator.compare(o1.get("native_name"), o2.get("native_name"));
            }
          });
          return ImmutableList.copyOf(list);
        }
      });

  private static final ImmutableMap<String, String> LOCALE_NAMES;
  static {
    ImmutableMap.Builder<String, String> localeNames = ImmutableMap.builder();
    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      localeNames.put(locale.toLanguageTag(), getDisplayName(locale, getLocaleDisplayNames(locale)));
    }
    LOCALE_NAMES = localeNames.build();
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
  private final BaseUrls baseUrls;

  @Inject SoyTemplateRenderer(SoyTofu soyTofu, SoyMsgBundleLoader soyMsgBundleLoader, BaseUrls baseUrls) {
    this.soyTofu = soyTofu;
    this.soyMsgBundleLoader = soyMsgBundleLoader;
    this.baseUrls = baseUrls;
  }

  public void render(SoyTemplate template, Appendable writer) {
    BrandInfo brandInfo = template.getBrandInfo();
    if (brandInfo == null) {
      brandInfo = new BrandInfo();
    }

    SoyMsgBundle msgBundle = soyMsgBundleLoader.get(template.getLocale());
    soyTofu.newRenderer(template.getTemplateInfo())
        .setData(template.getData())
        .setMsgBundle(msgBundle)
        .setIjData(ImmutableMap.of(
            "landing_page_url", baseUrls.landingPage().map(URI::toString).orElse(""),
            "current_locale", msgBundle.getLocaleString(),
            "locale_name_map", LOCALE_NAMES,
            "supported_locales", SUPPORTED_LOCALES.getUnchecked(msgBundle.getLocaleString()),
            "brand_info", BrandHelper.toMap(brandInfo)
        ))
        .setContentKind(template.getContentKind())
        .render(writer);
  }

  public String renderAsString(SoyTemplate template) {
    StringBuilder sb = new StringBuilder();
    render(template, sb);
    return sb.toString();
  }
}
