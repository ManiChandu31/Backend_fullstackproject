package com.pathfinder.controller;

import com.pathfinder.exception.DuplicateResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.pathfinder.exception.UnauthorizedAccessException;
import com.pathfinder.model.User;
import com.pathfinder.repository.UserRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.Optional;
import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final Logger log = LoggerFactory.getLogger(AuthController.class);

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/signup")
    public User signup(@RequestBody User user) {
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
