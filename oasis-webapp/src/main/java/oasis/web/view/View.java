package oasis.web.view;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class View {
  private final String path;
  private final Object model;

  public View(Class<?> base, String relativePath) {
    this(base, relativePath, null);
  }

  public View(Class<?> base, String relativePath, Object model) {
    this(base.getPackage().getName().replace('.', '/') + "/" + relativePath, model);
  }

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
