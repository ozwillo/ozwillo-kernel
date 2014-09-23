package oasis.mail;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import com.google.common.base.Throwables;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.parseinfo.SoyTemplateInfo;

public class MailMessage {
  private InternetAddress recipient;
  private SoyTemplateInfo subject;
  private SoyTemplateInfo body;
  private boolean html = true;
  private SoyMapData data;

  public InternetAddress getRecipient() {
    return recipient;
  }

  public MailMessage setRecipient(InternetAddress recipient) {
    this.recipient = recipient;
    return this;
  }

  public MailMessage setRecipient(String recipient) throws AddressException {
    this.recipient = new InternetAddress(recipient, true);
    return this;
  }

  public MailMessage setRecipient(String address, String personal) {
    try {
      this.recipient = new InternetAddress(address, personal, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      // We use a Unicode charset, so that shouldn't happen.
      Throwables.propagate(e);
    }
    return this;
  }

  public SoyTemplateInfo getSubject() {
    return subject;
  }

  public MailMessage setSubject(SoyTemplateInfo subject) {
    this.subject = subject;
    return this;
  }

  public SoyTemplateInfo getBody() {
    return body;
  }

  public MailMessage setBody(SoyTemplateInfo body) {
    this.body = body;
    return this;
  }

  public boolean isHtml() {
    return html;
  }

  public MailMessage setHtml() {
    this.html = true;
    return this;
  }

  public MailMessage setPlainText() {
    this.html = false;
    return this;
  }

  public SoyMapData getData() {
    return data;
  }

  public MailMessage setData(SoyMapData data) {
    this.data = data;
    return this;
  }
}
