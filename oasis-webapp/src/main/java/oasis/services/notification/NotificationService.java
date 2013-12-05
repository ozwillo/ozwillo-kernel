package oasis.services.notification;

import java.util.HashSet;
import java.util.Set;

import org.joda.time.Instant;

import com.google.common.collect.Lists;
import com.google.inject.Inject;

import oasis.model.directory.DirectoryRepository;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;

public class NotificationService {

  private final NotificationRepository notificationRepository;

  private final DirectoryRepository directoryRepository;

  @Inject
  NotificationService(NotificationRepository notificationRepository, DirectoryRepository directoryRepository) {
    this.notificationRepository = notificationRepository;
    this.directoryRepository = directoryRepository;
  }

  // TODO: handle exceptions
  public void createNotifications(String[] groupIds, String[] userIds, String data, String message, String applicationId) {
    Instant now = Instant.now();

    Set<String> allUserIds = new HashSet<>();

    if (groupIds != null) {
      for (String groupId : groupIds) {
        allUserIds.addAll(directoryRepository.getGroupMembers(groupId));
      }
    }

    if (userIds != null) {
      allUserIds.addAll(Lists.newArrayList(userIds));
    }

    for (String userId : allUserIds) {
      Notification notification = new Notification();
      notification.setApplicationId(applicationId);
      notification.setUserId(userId);
      notification.setData(data);
      notification.setMessage(message);

      notification.setTime(now);
      notification.setStatus(Notification.Status.UNREAD);

      notificationRepository.createNotification(notification);
    }

  }
}
