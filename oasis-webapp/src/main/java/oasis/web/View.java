package oasis.web;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class View {
  private final String path;
  private final Object model;

  public View(String path) {
    this(path, null);
  }

  public View(String path, Object model) {
    this.path = Preconditions.checkNotNull(Strings.emptyToNull(path));
    this.model = model;
  }

  public String getPath() {
    return path;
  }

  public Object getModel() {
    return model;
  }
}
