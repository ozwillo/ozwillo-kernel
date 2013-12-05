package oasis.model.notification;

import java.util.List;

public interface NotificationRepository {

  /**
   * @return the generated notification id
   */
  public Notification createNotification(Notification notification);

  public void deleteNotification(String notificationId);

  public Notification getNotification(String notificationId);

  public Iterable<Notification> getNotifications(String userId);

  public Iterable<Notification> getNotifications(String userId, String appId);

  public Iterable<Notification> getUnreadNotifications(String userId);

  public Iterable<Notification> getUnreadNotifications(String userId, String appId);

  public void markNotifications(String userId, List<String> notificationIds, Notification.Status status);

}
