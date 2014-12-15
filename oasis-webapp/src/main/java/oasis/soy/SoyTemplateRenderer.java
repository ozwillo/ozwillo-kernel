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
import com.ibm.icu.text.Collator;
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
          final ArrayList<SoyMapData> list = new ArrayList<>();
          for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
            SoyMapData localeDesc = new SoyMapData(
                "locale", locale.toLanguageTag(),
                // XXX: Use only the language for now, until we add locales for same language in different variants.
                "native_name", locale.getDisplayLanguage(locale),
                "translated_name", locale.getDisplayLanguage(currentLocale)
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
      // XXX: Use only the language for now, until we add locales for same language in different variants.
      localeNames.put(locale.toLanguageTag(), locale.getDisplayLanguage(locale));
    }
    LOCALE_NAMES = localeNames;
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
