package oasis.soy;

import javax.inject.Inject;

import com.google.template.soy.tofu.SoyTofu;

public class SoyTemplateRenderer {
  private final SoyTofu soyTofu;
  private final SoyMsgBundleLoader soyMsgBundleLoader;

  @Inject SoyTemplateRenderer(SoyTofu soyTofu, SoyMsgBundleLoader soyMsgBundleLoader) {
    this.soyTofu = soyTofu;
    this.soyMsgBundleLoader = soyMsgBundleLoader;
  }

  public void render(SoyTemplate template, Appendable writer) {
    SoyTofu.Renderer renderer = soyTofu.newRenderer(template.getTemplateInfo())
        .setData(template.getData())
        .setMsgBundle(soyMsgBundleLoader.get(template.getLocale()));
    if (template.getContentKind() != null) {
      renderer.setContentKind(template.getContentKind());
    }
    renderer.render(writer);
  }
}
