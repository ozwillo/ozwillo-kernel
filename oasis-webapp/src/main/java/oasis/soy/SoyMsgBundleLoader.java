package oasis.soy;

import static com.google.common.base.Objects.*;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.api.client.repackaged.com.google.common.base.Throwables;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgBundleImpl;

@Singleton
class SoyMsgBundleLoader {
  private static final ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);

  /** Sentinel value used to represent the 'null' value in the cache. */
  private static final SoyMsgBundle SENTINEL = new SoyMsgBundleImpl(null, Collections.<SoyMsg>emptyList());

  private final SoyMsgBundleHandler soyMsgBundleHandler;

  private final ConcurrentHashMap<Locale, SoyMsgBundle> cache = new ConcurrentHashMap<>();

  @Inject SoyMsgBundleLoader(SoyMsgBundleHandler soyMsgBundleHandler) {
    this.soyMsgBundleHandler = soyMsgBundleHandler;
  }

  /** Returns the corresponding bundle, or {@code null} if none has been found. */
  public SoyMsgBundle get(Locale locale) {
    SoyMsgBundle bundle = cache.get(locale);
    if (bundle == null) {
      for (Locale candidateLocale : control.getCandidateLocales("", locale)) {
        bundle = cache.get(candidateLocale);
        if (bundle != null) {
          break;
        }
        // Try load the bundle:
        URL resource = getClass().getClassLoader().getResource(control.toResourceName(control.toBundleName("templates.messages", locale), "xlf"));
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
      // Cache the bundle for the requested locale to avoid walking the candidate locales next time.
      // XXX: cache the bundle for all visited intermediate locales?
      cache.put(locale, firstNonNull(bundle, SENTINEL));
    }
    return bundle == SENTINEL ? null : bundle;
  }
}
