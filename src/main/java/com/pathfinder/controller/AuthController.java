package com.pathfinder.controller;

import com.pathfinder.exception.DuplicateResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.pathfinder.exception.UnauthorizedAccessException;
import com.pathfinder.model.User;
import com.pathfinder.repository.UserRepository;
import com.pathfinder.service.SignupOtpService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.Optional;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final SignupOtpService signupOtpService;
    private final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Value("${APP_MAX_STUDENTS:1000}")
    private int appMaxStudents;

    public AuthController(UserRepository userRepository, SignupOtpService signupOtpService) {
        this.userRepository = userRepository;
        this.signupOtpService = signupOtpService;
    }

    private void enforceStudentLimit() {
        try {
            long studentCount = userRepository.countByRole("student");
            if (studentCount >= appMaxStudents) {
                throw new IllegalArgumentException("Student registration limit reached");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to check student limit, allowing signup by default: {}", e.getMessage());
        }
    }

    @PostMapping("/signup")
    public User signup(@RequestBody User user) {
        enforceStudentLimit();
        if (userRepository.existsByUserId(user.getUserId())) {
            throw new DuplicateResourceException("User ID already exists!");
        }

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateResourceException("User already exists!");
        }
        
        user.setRole("student");
        try {
            return userRepository.save(user);
        } catch (Exception e) {
            log.error("Error saving user: {}", e.toString(), e);
            throw e;
        }
    }

    @PostMapping("/signup/request-otp")
    public ResponseEntity<?> requestSignupOtp(@RequestBody Map<String, String> payload) {
        enforceStudentLimit();
        String userId = payload.get("userId");
        String email = payload.get("email");
        String phoneNumber = payload.get("phoneNumber");
        String password = payload.get("password");

        if (userId == null || userId.isBlank() || email == null || email.isBlank() || phoneNumber == null || phoneNumber.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("userId, email, phoneNumber and password are required");
        }

        if ("admin@example.com".equalsIgnoreCase(email)) {
            throw new IllegalArgumentException("This email is reserved for admin login");
        }

        if (userRepository.existsByUserId(userId)) {
            throw new DuplicateResourceException("User ID already exists!");
        }

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("User already exists!");
        }

        return new ResponseEntity<>(signupOtpService.createOtp(userId, email, phoneNumber, password), HttpStatus.OK);
    }

    @PostMapping("/signup/verify-otp")
    public ResponseEntity<User> verifyOtpAndSignup(@RequestBody Map<String, String> payload) {
        String phoneNumber = payload.get("phoneNumber");
        String otp = payload.get("otp");

        if (phoneNumber == null || phoneNumber.isBlank() || otp == null || otp.isBlank()) {
            throw new IllegalArgumentException("phoneNumber and otp are required");
        }

        String normalizedPhone = signupOtpService.verifyOtpAndGetPhoneNumber(phoneNumber, otp);
        SignupOtpService.PendingSignup pendingSignup = signupOtpService.consumePendingSignup(normalizedPhone);

        if (pendingSignup == null) {
            throw new IllegalArgumentException("No pending signup found for this phone number");
        }

        if (userRepository.existsByUserId(pendingSignup.userId())) {
            throw new DuplicateResourceException("User ID already exists!");
        }

        if (userRepository.existsByEmail(pendingSignup.email())) {
            throw new DuplicateResourceException("User already exists!");
        }

        User user = new User();
        user.setUserId(pendingSignup.userId());
        user.setEmail(pendingSignup.email());
        user.setPhoneNumber(pendingSignup.phoneNumber());
        user.setPassword(pendingSignup.password());
        user.setRole("student");

        enforceStudentLimit();

        try {
            return new ResponseEntity<>(userRepository.save(user), HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Error saving verified user: {}", e.toString(), e);
            throw e;
        }
    }

    @PostMapping("/login")
    public Object login(@RequestBody java.util.Map<String, String> loginData) {
        String identifier = loginData.get("identifier");
        String password = loginData.get("password");
        String role = loginData.get("role");

        if ("faculty".equals(role)) {
            if ("admin@example.com".equals(identifier) && "Admin123".equals(password)) {
                java.util.Map<String, Object> response = new java.util.HashMap<>();
                response.put("message", "Admin Login Successful!");
                response.put("role", "admin");
                return response;
            } else {
                throw new UnauthorizedAccessException("Incorrect faculty credentials");
            }
        }

        if ("admin@example.com".equals(identifier)) {
            throw new IllegalArgumentException("For admin account, select Faculty role and login");
        }

        Optional<User> userOptional = userRepository.findByEmail(identifier);
        if (!userOptional.isPresent()) {
            userOptional = userRepository.findByUserId(identifier);
        }

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getPassword().equals(password)) {
                java.util.Map<String, Object> response = new java.util.HashMap<>();
                response.put("message", "User Login Successful!");
                response.put("role", "user");
                response.put("userId", user.getUserId());
                response.put("email", user.getEmail());
                return response;
            }
        }

        throw new UnauthorizedAccessException("Incorrect email or password");
    }

    // DEBUG: temporary endpoint to list users for verification
    @GetMapping("/users")
    public List<User> listUsers() {
        return userRepository.findAll();
    }
}
