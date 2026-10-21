package com.devhub.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class OtpEmailSender {
    private final JavaMailSender mailSender;
    private final String from;

    public OtpEmailSender(JavaMailSender mailSender, @Value("${MAIL_FROM:no-reply@orbit.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String email, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Your Orbit verification code");
        message.setText("Your Orbit registration code is " + otp + ". It expires in 10 minutes. If you did not request it, ignore this email.");
        mailSender.send(message);
    }
}
