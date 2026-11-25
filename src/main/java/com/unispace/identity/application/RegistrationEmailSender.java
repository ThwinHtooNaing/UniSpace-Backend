package com.unispace.identity.application;

public interface RegistrationEmailSender {
    void send(String destination, String code);

    final class DeliveryFailed extends RuntimeException {}
}
