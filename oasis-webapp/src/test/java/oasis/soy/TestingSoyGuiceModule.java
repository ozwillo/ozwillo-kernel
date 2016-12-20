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
