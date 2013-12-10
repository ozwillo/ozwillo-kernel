package oasis.jongo.notification;

import java.util.List;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.WriteResult;

import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;

public class JongoNotificationRepository implements NotificationRepository {

  private static final Logger logger = LoggerFactory.getLogger(NotificationRepository.class);

  private final Jongo jongo;

  @Inject
  JongoNotificationRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  private MongoCollection getNotificationCollection() {
    return jongo.getCollection("notification");
  }

  @Override
  public Notification createNotification(Notification notification) {
    JongoNotification jongoNotification = new JongoNotification(notification);
    getNotificationCollection().insert(jongoNotification);
    return jongoNotification;
  }

  @Override
  public boolean deleteNotification(String notificationId) {
    WriteResult wr = getNotificationCollection().remove("{ id: # }", notificationId);

    if (wr.getN() != 1 && logger.isWarnEnabled()) {
      logger.warn("The notification {} does not exist.", notificationId);
    }
    return wr.getN() == 1;
  }

  @Override
  public Notification getNotification(String notificationId) {
    return getNotificationCollection()
        .findOne("{ id: # }", notificationId)
        .as(JongoNotification.class);
  }

  @Override
  public Iterable<Notification> getNotifications(String userId) {
    return (Iterable<Notification>) (Iterable<?>) getNotificationCollection()
        .find("{ userId: # }", userId)
        .as(JongoNotification.class);
  }

  @Override
  public Iterable<Notification> getNotifications(String userId, String appId) {
    return (Iterable<Notification>) (Iterable<?>) getNotificationCollection()
        .find("{ userId: #, applicationId: # }", userId, appId)
        .as(JongoNotification.class);
  }

  @Override
  public Iterable<Notification> getUnreadNotifications(String userId) {
    return (Iterable<Notification>) (Iterable<?>) getNotificationCollection()
        .find("{ userId: #, status: # }", userId, Notification.Status.UNREAD)
        .as(JongoNotification.class);
  }

  @Override
  public Iterable<Notification> getUnreadNotifications(String userId, String appId) {
    return (Iterable<Notification>) (Iterable<?>) getNotificationCollection()
        .find("{ userId: #, applicationId: #, status: # }", userId, appId, Notification.Status.UNREAD)
        .as(JongoNotification.class);
  }

  @Override
  public void markNotifications(String userId, List<String> notificationIds, Notification.Status status) {
    getNotificationCollection()
        .update("{ id: {$in: # }, userId: # }", notificationIds, userId)
        .multi()
        .with("{ $set: { status: # } }", status);
  }

}
