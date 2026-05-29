package com.amazondemo.notification.service;

import com.amazondemo.notification.model.Notification;
import com.amazondemo.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for NotificationService.
 *
 * Tests cover:
 * - Create and persist notification
 * - Send email via JavaMailSender
 * - Query notifications (all, unread, count)
 * - Mark notification as read
 * - Email send failure resilience
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Unit Tests")
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private JavaMailSender mailSender;

    @InjectMocks
    private NotificationService notificationService;

    private static final String USER_ID = "user-001";

    private Notification buildNotification(String id, boolean read) {
        return Notification.builder()
            .id(id)
            .userId(USER_ID)
            .title("Order Placed")
            .message("Your order ORD-2026-000001 has been placed.")
            .type("ORDER")
            .referenceId("order-001")
            .referenceType("ORDER")
            .read(read)
            .build();
    }

    // ==================== CREATE NOTIFICATION ====================

    @Nested
    @DisplayName("createNotification()")
    class CreateNotificationTests {

        @Test
        @DisplayName("Should save notification with all provided fields")
        void shouldSaveNotificationWithCorrectFields() {
            when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            notificationService.createNotification(
                USER_ID, "Order Placed", "Your order is confirmed",
                "ORDER", "order-001", "ORDER"
            );

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            Notification saved = captor.getValue();

            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getTitle()).isEqualTo("Order Placed");
            assertThat(saved.getType()).isEqualTo("ORDER");
            assertThat(saved.getReferenceId()).isEqualTo("order-001");
        }

        @Test
        @DisplayName("Should save notification as unread by default")
        void shouldSaveAsUnreadByDefault() {
            when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            notificationService.createNotification(
                USER_ID, "Test", "Message", "INFO", null, null
            );

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().isRead()).isFalse();
        }
    }

    // ==================== SEND EMAIL ====================

    @Nested
    @DisplayName("sendEmail()")
    class SendEmailTests {

        @Test
        @DisplayName("Should send email via JavaMailSender with correct fields")
        void shouldSendEmailWithCorrectFields() {
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            notificationService.sendEmail("user@example.com", "Order Confirmed", "Dear User...");

            ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());

            SimpleMailMessage sent = captor.getValue();
            assertThat(sent.getTo()).contains("user@example.com");
            assertThat(sent.getSubject()).isEqualTo("Order Confirmed");
            assertThat(sent.getText()).isEqualTo("Dear User...");
            assertThat(sent.getFrom()).isEqualTo("noreply@amazondemo.com");
        }

        @Test
        @DisplayName("Should not throw when mail sender fails (graceful degradation)")
        void shouldNotThrowWhenMailSenderFails() {
            doThrow(new RuntimeException("SMTP connection refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));

            // Should handle the error gracefully and NOT propagate it
            assertThatCode(() ->
                notificationService.sendEmail("user@example.com", "Subject", "Body"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should attempt to send to correct recipient")
        void shouldSendToCorrectRecipient() {
            doNothing().when(mailSender).send(any(SimpleMailMessage.class));

            notificationService.sendEmail("test@domain.com", "Subject", "Body");

            ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getTo()).containsExactly("test@domain.com");
        }
    }

    // ==================== QUERY NOTIFICATIONS ====================

    @Nested
    @DisplayName("getUserNotifications()")
    class GetNotificationsTests {

        @Test
        @DisplayName("Should return all notifications for user in descending order")
        void shouldReturnAllUserNotifications() {
            List<Notification> notifications = List.of(
                buildNotification("n-003", true),
                buildNotification("n-002", false),
                buildNotification("n-001", true)
            );
            when(notificationRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(notifications);

            List<Notification> result = notificationService.getUserNotifications(USER_ID);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).getId()).isEqualTo("n-003");
        }

        @Test
        @DisplayName("Should return only unread notifications")
        void shouldReturnOnlyUnreadNotifications() {
            List<Notification> unread = List.of(buildNotification("n-002", false));
            when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(USER_ID))
                .thenReturn(unread);

            List<Notification> result = notificationService.getUnreadNotifications(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).isRead()).isFalse();
        }

        @Test
        @DisplayName("Should return correct unread count")
        void shouldReturnUnreadCount() {
            when(notificationRepository.countByUserIdAndReadFalse(USER_ID)).thenReturn(5L);

            long count = notificationService.getUnreadCount(USER_ID);

            assertThat(count).isEqualTo(5L);
        }
    }

    // ==================== MARK AS READ ====================

    @Nested
    @DisplayName("markAsRead()")
    class MarkAsReadTests {

        @Test
        @DisplayName("Should mark notification as read and save")
        void shouldMarkNotificationAsRead() {
            Notification notification = buildNotification("n-001", false);
            when(notificationRepository.findById("n-001"))
                .thenReturn(Optional.of(notification));
            when(notificationRepository.save(any())).thenReturn(notification);

            notificationService.markAsRead("n-001");

            assertThat(notification.isRead()).isTrue();
            verify(notificationRepository).save(notification);
        }

        @Test
        @DisplayName("Should do nothing when notification not found")
        void shouldDoNothingWhenNotificationNotFound() {
            when(notificationRepository.findById("ghost-notif"))
                .thenReturn(Optional.empty());

            assertThatCode(() -> notificationService.markAsRead("ghost-notif"))
                .doesNotThrowAnyException();

            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should not change already-read notification")
        void shouldHandleAlreadyReadNotification() {
            Notification alreadyRead = buildNotification("n-001", true);
            when(notificationRepository.findById("n-001"))
                .thenReturn(Optional.of(alreadyRead));
            when(notificationRepository.save(any())).thenReturn(alreadyRead);

            notificationService.markAsRead("n-001");

            assertThat(alreadyRead.isRead()).isTrue(); // still true
        }
    }
}
