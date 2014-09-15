package oasis.soy;

import javax.inject.Inject;

import com.google.template.soy.tofu.SoyTofu;

public class SoyTemplateRenderer {
  private final SoyTofu soyTofu;

  @Inject SoyTemplateRenderer(SoyTofu soyTofu) {
    this.soyTofu = soyTofu;
  }

  public void render(SoyTemplate template, Appendable writer) {
    SoyTofu.Renderer renderer = soyTofu.newRenderer(template.getTemplateInfo())
        .setData(template.getData());
    if (template.getContentKind() != null) {
      renderer.setContentKind(template.getContentKind());
    }
    renderer.render(writer);
  }
}
