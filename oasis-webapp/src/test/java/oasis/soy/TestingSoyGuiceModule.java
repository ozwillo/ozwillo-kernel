package oasis.soy;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.conformance.CheckConformance;
import com.google.template.soy.soytree.SoyFileSetNode;

/**
 * Fix SoyGuiceModule when used with Jukito.
 *
 * <p>Closure Templates makes use of optional injection, which Jukito doesn't support.
 *
 * @see <a href=https://github.com/ArcBees/Jukito/issues/61>Jukito bug report</a>
 */
public class TestingSoyGuiceModule extends SoyGuiceModule {
  @Provides @Singleton
  CheckConformance provideCheckConformance() {
    return new CheckConformance() {
      @Override
      public ImmutableList<SoySyntaxException> getViolations(SoyFileSetNode root) {
        return ImmutableList.of();
      }
    };
  }
}
