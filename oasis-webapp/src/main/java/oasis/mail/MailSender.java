package oasis.mail;

import java.nio.charset.StandardCharsets;

import javax.inject.Inject;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;

import com.google.common.base.CharMatcher;
import com.google.template.soy.data.SanitizedContent;

import oasis.soy.SoyTemplate;
import oasis.soy.SoyTemplateRenderer;

public class MailSender {
  private final MailModule.Settings settings;
  private final Session session;
  private final SoyTemplateRenderer templateRenderer;

  @Inject MailSender(MailModule.Settings settings, Session session, SoyTemplateRenderer templateRenderer) {
    this.settings = settings;
    this.session = session;
    this.templateRenderer = templateRenderer;
  }

  public void send(MailMessage message) throws MessagingException {
    StringBuilder subject = new StringBuilder();
    templateRenderer.render(new SoyTemplate(message.getSubject(), message.getLocale(), SanitizedContent.ContentKind.TEXT, message.getData()), subject);

    StringBuilder body = new StringBuilder();
    templateRenderer.render(new SoyTemplate(message.getBody(), message.getLocale(), message.isHtml() ? SanitizedContent.ContentKind.HTML : SanitizedContent.ContentKind.TEXT,
        message.getData()), body);

    MimeMessage msg = new MimeMessage(session);
    msg.setFrom(settings.from);
    msg.setRecipient(Message.RecipientType.TO, message.getRecipient());
    msg.setSubject(CharMatcher.WHITESPACE.trimAndCollapseFrom(subject.toString(), ' '));
    msg.setText(body.toString(), StandardCharsets.UTF_8.name(), message.isHtml() ? "html" : "plain");

    Transport.send(msg);
  }
}
