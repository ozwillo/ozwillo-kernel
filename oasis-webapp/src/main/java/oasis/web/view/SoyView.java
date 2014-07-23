package oasis.web.view;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.parseinfo.SoyTemplateInfo;

public class SoyView {
  private final SoyTemplateInfo templateInfo;
  private final @Nullable SanitizedContent.ContentKind contentKind;
  private final @Nullable SoyMapData data;

  public SoyView(SoyTemplateInfo templateInfo) {
    this(templateInfo, null);
  }

  public SoyView(SoyTemplateInfo templateInfo, @Nullable SoyMapData data) {
    this(templateInfo, null, data);
  }

  public SoyView(SoyTemplateInfo templateInfo, @Nullable SanitizedContent.ContentKind contentKind, @Nullable SoyMapData data) {
    this.templateInfo = Preconditions.checkNotNull(templateInfo);
    this.contentKind = contentKind;
    this.data = data;
  }

  public SoyTemplateInfo getTemplateInfo() {
    return templateInfo;
  }

  @Nullable
  public SanitizedContent.ContentKind getContentKind() {
    return contentKind;
  }

  @Nullable
  public SoyMapData getData() {
    return data;
  }
}
