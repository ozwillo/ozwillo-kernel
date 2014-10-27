package oasis.jongo.notification;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.mongodb.DuplicateKeyException;
import com.mongodb.WriteResult;

import oasis.jongo.JongoBootstrapper;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;

public class JongoNotificationRepository implements NotificationRepository, JongoBootstrapper {

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
    try {
      getNotificationCollection().insert(jongoNotification);
    } catch (DuplicateKeyException e) {
      return null;
    }
    return jongoNotification;
  }

  @Override
  public List<Notification> createNotifications(List<Notification> notifications) {
    List<Notification> jongoNotifications = new ArrayList<>(notifications.size());
    for (Notification notification : notifications) {
      jongoNotifications.add(new JongoNotification(notification));
    }
    try {
      getNotificationCollection().insert(jongoNotifications.toArray());
    } catch (DuplicateKeyException e) {
      return null;
    }
    return jongoNotifications;
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
        .find("{ user_id: # }", userId)
        .as(JongoNotification.class);
  }

  @Override
  public Iterable<Notification> getNotifications(String userId, String instanceId) {
    return (Iterable<Notification>) (Iterable<?>) getNotificationCollection()
        .find("{ user_id: #, instance_id: # }", userId, instanceId)
        .as(JongoNotification.class);
  }

  @Override
  public Iterable<Notification> getUnreadNotifications(String userId) {
    return (Iterable<Notification>) (Iterable<?>) getNotificationCollection()
        .find("{ user_id: #, status: # }", userId, Notification.Status.UNREAD)
        .as(JongoNotification.class);
  }

  @Override
  public Iterable<Notification> getUnreadNotifications(String userId, String instanceId) {
    return (Iterable<Notification>) (Iterable<?>) getNotificationCollection()
        .find("{ user_id: #, instance_id: #, status: # }", userId, instanceId, Notification.Status.UNREAD)
        .as(JongoNotification.class);
  }

  @Override
  public void markNotifications(String userId, List<String> notificationIds, Notification.Status status) {
    getNotificationCollection()
        .update("{ id: {$in: # }, user_id: # }", notificationIds, userId)
        .multi()
        .with("{ $set: { status: # } }", status);
  }

  @Override
  public void bootstrap() {
    getNotificationCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    // TODO: add indexes for all other accesses
  }
}
