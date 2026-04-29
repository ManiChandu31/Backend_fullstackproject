package com.pathfinder.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SignupOtpService {

    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final String TWILIO_API_BASE = "https://api.twilio.com/2010-04-01/Accounts";

    private final Logger log = LoggerFactory.getLogger(SignupOtpService.class);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, PendingSignup> pendingSignups = new ConcurrentHashMap<>();

    public OtpRequestResult createOtp(String userId, String email, String phoneNumber, String password) {
        String otp = generateOtp();
        Instant expiresAt = Instant.now().plus(OTP_TTL);

        String normalizedPhone = normalizePhone(phoneNumber);
        pendingSignups.put(normalizedPhone, new PendingSignup(userId, email.toLowerCase(), normalizedPhone, password, otp, expiresAt));

        boolean sent = sendOtpToPhone(normalizedPhone, otp);
        if (!sent) {
            log.info("OTP for {} is {}", normalizedPhone, otp);
        }

        return new OtpRequestResult(
                normalizedPhone,
                sent ? "SMS" : "CONSOLE",
                sent ? null : otp,
                expiresAt
        );
    }

    public String verifyOtpAndGetPhoneNumber(String phoneNumber, String otp) {
        String normalizedPhone = normalizePhone(phoneNumber);
        PendingSignup pending = pendingSignups.get(normalizedPhone);

        if (pending == null) {
            throw new IllegalArgumentException("No OTP request found for this phone number");
        }

        if (Instant.now().isAfter(pending.expiresAt())) {
            pendingSignups.remove(normalizedPhone);
            throw new IllegalArgumentException("OTP has expired. Please request a new one.");
        }

        if (!pending.otp().equals(otp)) {
            throw new IllegalArgumentException("Invalid OTP code");
        }

        return normalizedPhone;
    }

    public PendingSignup consumePendingSignup(String phoneNumber) {
        return pendingSignups.remove(normalizePhone(phoneNumber));
    }

    private boolean sendOtpToPhone(String phoneNumber, String otp) {
        String accountSid = System.getenv("TWILIO_ACCOUNT_SID");
        String authToken = System.getenv("TWILIO_AUTH_TOKEN");
        String fromNumber = System.getenv("TWILIO_FROM_NUMBER");

        if (isBlank(accountSid) || isBlank(authToken) || isBlank(fromNumber)) {
            return false;
        }

        try {
            String messageBody = "Your PathFinder OTP is: " + otp + ". It expires in 10 minutes.";
            String endpoint = TWILIO_API_BASE + "/" + accountSid + "/Messages.json";
            String formData = buildFormData(Map.of(
                    "To", phoneNumber,
                    "From", fromNumber,
                    "Body", messageBody
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", buildBasicAuth(accountSid, authToken))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }

            log.warn("Twilio SMS request failed for {} with status {} and body {}", phoneNumber, response.statusCode(), response.body());
            return false;
        } catch (Exception ex) {
            log.warn("Failed to send OTP to {}: {}", phoneNumber, ex.getMessage());
            return false;
        }
    }

    private String buildBasicAuth(String accountSid, String authToken) {
        String token = accountSid + ":" + authToken;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private String buildFormData(Map<String, String> data) {
        return data.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String normalizePhone(String phoneNumber) {
        return phoneNumber == null ? "" : phoneNumber.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String generateOtp() {
        int code = (int) (Math.random() * 900000) + 100000;
        return String.valueOf(code);
    }

    public record PendingSignup(String userId, String email, String phoneNumber, String password, String otp, Instant expiresAt) {
    }

    public record OtpRequestResult(String phoneNumber, String deliveryMode, String verificationCode, Instant expiresAt) {
    }
}