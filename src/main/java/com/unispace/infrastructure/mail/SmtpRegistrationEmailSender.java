package com.unispace.infrastructure.mail;

import com.unispace.identity.application.RegistrationEmailSender;
import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "unispace.auth.otp.smtp", name = "host")
class SmtpRegistrationEmailSender implements RegistrationEmailSender {
    private final JavaMailSenderImpl mail;
    private final String from;

    SmtpRegistrationEmailSender(Environment environment) {
        String prefix = "unispace.auth.otp.smtp.";
        from = environment.getRequiredProperty(prefix + "from");
        mail = new JavaMailSenderImpl();
        mail.setHost(environment.getRequiredProperty(prefix + "host"));
        mail.setPort(environment.getProperty(prefix + "port", Integer.class, 587));
        mail.setUsername(environment.getRequiredProperty(prefix + "username"));
        mail.setPassword(environment.getRequiredProperty(prefix + "password"));
        var properties = new Properties();
        properties.setProperty("mail.debug", "false");
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
        mail.setJavaMailProperties(properties);
    }

    @Override
    public void send(String destination, String code) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(destination);
        message.setSubject("Verify your UniSpace university email");
        message.setText("Your UniSpace verification code is " + code + ". It expires in 10 minutes.");
        try {
            mail.send(message);
        } catch (MailException e) {
            // The provider's exception may include message or recipient data.
            throw new DeliveryFailed();
        }
    }
}
