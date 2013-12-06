package oasis.model.notification;

import java.util.List;

public interface NotificationRepository {

  /**
   * @return the generated notification id
   */
  Notification createNotification(Notification notification);

  void deleteNotification(String notificationId);

  Notification getNotification(String notificationId);

  Iterable<Notification> getNotifications(String userId);

  Iterable<Notification> getNotifications(String userId, String appId);

  Iterable<Notification> getUnreadNotifications(String userId);

  Iterable<Notification> getUnreadNotifications(String userId, String appId);

  void markNotifications(String userId, List<String> notificationIds, Notification.Status status);

}
