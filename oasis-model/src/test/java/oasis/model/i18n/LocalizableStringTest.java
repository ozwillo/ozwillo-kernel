package oasis.model.i18n;

import static org.assertj.core.api.Assertions.*;

import java.util.Locale;

import org.junit.Test;

public class LocalizableStringTest {

  @Test
  public void testExact() {
    LocalizableString sut = new LocalizableString();
    sut.set(Locale.ROOT, "root");
    sut.set(Locale.FRENCH, "Français");
    sut.set(Locale.forLanguageTag("fr-BE"), "Français (Belgique)");

    assertThat(sut.get(Locale.ROOT)).isEqualTo("root");

    assertThat(sut.get(Locale.FRENCH)).isEqualTo("Français");
    assertThat(sut.get(Locale.FRANCE)).isEqualTo("Français");

    assertThat(sut.get(Locale.forLanguageTag("fr-BE"))).isEqualTo("Français (Belgique)");

    assertThat(sut.get(Locale.forLanguageTag("fr-LU"))).isEqualTo("Français");

    assertThat(sut.get(Locale.ENGLISH)).isEqualTo("root");
    assertThat(sut.get(Locale.UK)).isEqualTo("root");
  }

  @Test
  public void testFallback() {
    LocalizableString sut = new LocalizableString();
    sut.set(Locale.ROOT, "root");
    sut.set(Locale.FRENCH, "Français");
    sut.set(Locale.forLanguageTag("fr-BE"), "Français (Belgique)");

    assertThat(sut.get(Locale.FRANCE)).isEqualTo("Français");
    assertThat(sut.get(Locale.forLanguageTag("fr-whatever"))).isEqualTo("Français");
    assertThat(sut.get(Locale.forLanguageTag("fr-x-whatever"))).isEqualTo("Français");
    assertThat(sut.get(Locale.forLanguageTag("fr-FR-whatever"))).isEqualTo("Français");
    assertThat(sut.get(Locale.forLanguageTag("fr-FR-x-whatever"))).isEqualTo("Français");
    assertThat(sut.get(Locale.forLanguageTag("fr-FR-whatever-x-whatever"))).isEqualTo("Français");
    assertThat(sut.get(Locale.forLanguageTag("fr-Latn-FR"))).isEqualTo("Français");
    assertThat(sut.get(Locale.forLanguageTag("fr-Latn-FR-whatever"))).isEqualTo("Français");
    assertThat(sut.get(Locale.forLanguageTag("fr-Latn-FR-x-whatever"))).isEqualTo("Français");
    assertThat(sut.get(Locale.forLanguageTag("fr-Latn-FR-whatever-x-whatever"))).isEqualTo("Français");

    assertThat(sut.get(Locale.forLanguageTag("fr-LU"))).isEqualTo("Français");

    assertThat(sut.get(Locale.forLanguageTag("fr-BE-whatever"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(Locale.forLanguageTag("fr-BE-x-whatever"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(Locale.forLanguageTag("fr-BE-whatever-x-whatever"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(Locale.forLanguageTag("fr-Latn-BE"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(Locale.forLanguageTag("fr-Latn-BE-whatever"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(Locale.forLanguageTag("fr-Latn-BE-x-whatever"))).isEqualTo("Français (Belgique)");
    assertThat(sut.get(Locale.forLanguageTag("fr-Latn-BE-whatever-x-whatever"))).isEqualTo("Français (Belgique)");

    assertThat(sut.get(Locale.ENGLISH)).isEqualTo("root");
    assertThat(sut.get(Locale.UK)).isEqualTo("root");
  }
}
