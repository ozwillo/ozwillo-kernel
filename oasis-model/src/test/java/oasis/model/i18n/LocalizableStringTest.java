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
package oasis.model.i18n;

import static org.assertj.core.api.Assertions.*;

import org.junit.Test;

import com.ibm.icu.util.ULocale;

public class LocalizableStringTest {

  @Test
  public void testExact() {
    LocalizableString sut = new LocalizableString();
    sut.set(ULocale.ROOT, "root");
    sut.set(ULocale.FRENCH, "Français");
    sut.set(ULocale.forLanguageTag("fr-BE"), "Français (Belgique)");

    assertThat(sut.get(ULocale.ROOT)).isEqualTo("root");

    assertThat(sut.get(ULocale.FRENCH)).isEqualTo("Français");
    assertThat(sut.get(ULocale.FRANCE)).isEqualTo("Français");

    assertThat(sut.get(ULocale.forLanguageTag("fr-BE"))).isEqualTo("Français (Belgique)");

    assertThat(sut.get(ULocale.forLanguageTag("fr-LU"))).isEqualTo("Français");

    assertThat(sut.get(ULocale.ENGLISH)).isEqualTo("root");
    assertThat(sut.get(ULocale.UK)).isEqualTo("root");
  }

  @Test
  public void testFallback() {
    LocalizableString sut = new LocalizableString();
    sut.set(ULocale.ROOT, "root");
    sut.set(ULocale.FRENCH, "Français");
    sut.set(ULocale.forLanguageTag("fr-BE"), "Français (Belgique)");

    assertThat(sut.get(ULocale.FRANCE)).isEqualTo("Français");
    assertThat(sut.get(ULocale.forLanguageTag("fr-whatever"))).isEqualTo("Français");
    assertThat(sut.get(ULocale.forLanguageTag("fr-x-whatever"))).isEqualTo("Français");
    assertThat(sut.get(ULocale.forLanguageTag("fr-FR-whatever"))).isEqualTo("Français");
    assertThat(sut.get(ULocale.forLanguageTag("fr-FR-x-whatever"))).isEqualTo("Français");
    assertThat(sut.get(ULocale.forLanguageTag("fr-FR-whatever-x-whatever"))).isEqualTo("Français");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-FR"))).isEqualTo("Français");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-FR-whatever"))).isEqualTo("Français");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-FR-x-whatever"))).isEqualTo("Français");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-FR-whatever-x-whatever"))).isEqualTo("Français");

    assertThat(sut.get(ULocale.forLanguageTag("fr-LU"))).isEqualTo("Français");

    assertThat(sut.get(ULocale.forLanguageTag("fr-BE-whatever"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-BE-x-whatever"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-BE-whatever-x-whatever"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-BE"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-BE-whatever"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-BE-x-whatever"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-BE-whatever-x-whatever"))).isEqualTo("Français (Belgique)");

    assertThat(sut.get(ULocale.ENGLISH)).isEqualTo("root");
    assertThat(sut.get(ULocale.UK)).isEqualTo("root");
  }

  @Test
  public void testFallbackToPossiblyMoreSpecificVariant() {
    LocalizableString sut = new LocalizableString();
    sut.set(ULocale.FRANCE, "Français (France)");
    sut.set(ULocale.forLanguageTag("fr-BE"), "Français (Belgique)");
    // XXX: insert after 'fr' to test that ROOT matches some 'en' flavor, independent of insertion order
    sut.set(ULocale.UK, "English (UK)");

    assertThat(sut.get(ULocale.FRENCH)).isEqualTo("Français (France)");
    assertThat(sut.get(ULocale.FRANCE)).isEqualTo("Français (France)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-whatever"))).isEqualTo("Français (France)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-x-whatever"))).isEqualTo("Français (France)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-FR-whatever"))).isEqualTo("Français (France)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-FR-x-whatever"))).isEqualTo("Français (France)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-FR-whatever-x-whatever"))).isEqualTo("Français (France)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-FR"))).isEqualTo("Français (France)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-FR-whatever"))).isEqualTo("Français (France)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-FR-x-whatever"))).isEqualTo("Français (France)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-FR-whatever-x-whatever"))).isEqualTo("Français (France)");

    // XXX: insertion order matters in this case
    assertThat(sut.get(ULocale.forLanguageTag("fr-LU"))).isEqualTo("Français (France)");

    assertThat(sut.get(ULocale.forLanguageTag("fr-BE-whatever"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-BE-x-whatever"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-BE-whatever-x-whatever"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-BE"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-BE-whatever"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-BE-x-whatever"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(ULocale.forLanguageTag("fr-Latn-BE-whatever-x-whatever"))).isEqualTo("Français (Belgique)");

    assertThat(sut.get(ULocale.ROOT)).isEqualTo("English (UK)");
    assertThat(sut.get(ULocale.ENGLISH)).isEqualTo("English (UK)");
    assertThat(sut.get(ULocale.US)).isEqualTo("English (UK)");
  }
}
