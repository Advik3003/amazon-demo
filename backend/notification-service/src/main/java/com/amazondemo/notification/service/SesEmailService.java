package com.amazondemo.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

/**
 * AWS SES email service.
 * Used in staging (LocalStack SES) and production (real AWS SES).
 *
 * In local environment, the regular SMTP/Mailhog sender is used instead.
 * Active when: app.aws.ses.enabled=true
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "app.aws.ses.enabled", havingValue = "true")
public class SesEmailService {

    @Value("${app.notification.from-email:noreply@amazondemo.com}")
    private String fromEmail;

    @Value("${app.notification.from-name:Amazon Demo}")
    private String fromName;

    private final SesClient sesClient;

    @Autowired
    public SesEmailService(SesClient sesClient) {
        this.sesClient = sesClient;
    }

    /**
     * Send a plain-text email via AWS SES.
     * In staging: uses LocalStack SES (email not actually delivered).
     * In prod: real delivery via AWS SES (requires verified sender identity).
     */
    public void sendEmail(String to, String subject, String body) {
        try {
            SendEmailRequest request = SendEmailRequest.builder()
                    .source(fromName + " <" + fromEmail + ">")
                    .destination(Destination.builder().toAddresses(to).build())
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .text(Content.builder().data(body).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build();

            SendEmailResponse response = sesClient.sendEmail(request);
            log.info("SES email sent to: {} messageId: {}", to, response.messageId());
        } catch (SesException e) {
            log.error("SES failed to send email to: {} - {}", to, e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            log.error("Failed to send SES email to: {} - {}", to, e.getMessage());
        }
    }

    /**
     * Send an HTML email via AWS SES.
     */
    public void sendHtmlEmail(String to, String subject, String htmlBody, String textBody) {
        try {
            SendEmailRequest request = SendEmailRequest.builder()
                    .source(fromName + " <" + fromEmail + ">")
                    .destination(Destination.builder().toAddresses(to).build())
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                    .text(Content.builder().data(textBody).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build();

            sesClient.sendEmail(request);
            log.info("SES HTML email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send SES HTML email to: {} - {}", to, e.getMessage());
        }
    }
}
