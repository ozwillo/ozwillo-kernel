package oasis.model.applications.v2;

import com.ibm.icu.util.ULocale;

import oasis.model.i18n.LocalizableString;

public class SimpleCatalogEntry extends CatalogEntry {
  private EntryType type;

  public SimpleCatalogEntry() {
  }

  /**
   * Copy constructor.
   * <p>
   * Does not copy {@link #id} field.
   */
  public SimpleCatalogEntry(CatalogEntry other) {
    super(other);
  }

  @Override
  public EntryType getType() {
    return type;
  }

  public void setType(EntryType type) {
    this.type = type;
  }

  public void restrictLocale(ULocale locale) {
    setName(new LocalizableString(getName().get(locale)));
    setDescription(new LocalizableString(getDescription().get(locale)));
    setIcon(new LocalizableString(getIcon().get(locale)));
    setTos_uri(new LocalizableString(getTos_uri().get(locale)));
    setPolicy_uri(new LocalizableString(getPolicy_uri().get(locale)));
  }
}
