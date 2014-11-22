package oasis.soy;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.base.Throwables;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.ibm.icu.util.ULocale;

import oasis.web.i18n.LocaleHelper;

@Singleton
class SoyMsgBundleLoader {
  private static final ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);

  private static final SoyMsgBundle DEFAULT = new SoyMsgBundle() {
    final String localeString = LocaleHelper.DEFAULT_LOCALE.toLanguageTag();

    @Override
    public String getLocaleString() {
      return localeString;
    }

    @Override
    public SoyMsg getMsg(long l) {
      return null;
    }

    @Override
    public int getNumMsgs() {
      return 0;
    }

    @Override
    public Iterator<SoyMsg> iterator() {
      return Collections.emptyIterator();
    }
  };

  private final SoyMsgBundleHandler soyMsgBundleHandler;

  private final ConcurrentHashMap<Locale, SoyMsgBundle> cache = new ConcurrentHashMap<>();

  @Inject SoyMsgBundleLoader(SoyMsgBundleHandler soyMsgBundleHandler) {
    this.soyMsgBundleHandler = soyMsgBundleHandler;
  }

  /** Returns the corresponding bundle. */
  public SoyMsgBundle get(ULocale locale) {
    SoyMsgBundle bundle = cache.get(locale.toLocale());
    if (bundle == null) {
      for (Locale candidateLocale : control.getCandidateLocales("", locale.toLocale())) {
        bundle = cache.get(candidateLocale);
        if (bundle != null) {
          break;
        }
        // Try load the bundle:
        URL resource = getClass().getClassLoader().getResource(control.toResourceName(control.toBundleName("templates.messages", candidateLocale), "xlf"));
        if (resource != null) {
          try {
            bundle = soyMsgBundleHandler.createFromResource(resource);
          } catch (IOException e) {
            throw Throwables.propagate(e);
          }
          cache.put(candidateLocale, bundle);
          break;
        }
      }
      if (bundle == null) {
        bundle = DEFAULT;
      }
      // Cache the bundle for the requested locale to avoid walking the candidate locales next time.
      // XXX: cache the bundle for all visited intermediate locales?
      cache.put(locale.toLocale(), bundle);
    }
    return bundle;
  }
}
