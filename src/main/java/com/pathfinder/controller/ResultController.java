package com.pathfinder.controller;

import com.pathfinder.exception.ResourceNotFoundException;
import com.pathfinder.model.Feedback;
import com.pathfinder.model.TestResult;
import com.pathfinder.repository.FeedbackRepository;
import com.pathfinder.repository.TestResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/api")
public class ResultController {

    @Autowired
    private TestResultRepository testResultRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    // --- TEST RESULTS ---

    @PostMapping("/results")
    public ResponseEntity<TestResult> saveResult(@RequestBody TestResult result) {
        TestResult savedResult = testResultRepository.save(result);
        return new ResponseEntity<>(savedResult, HttpStatus.CREATED);
    }

    @GetMapping("/results")
    public ResponseEntity<List<TestResult>> getResults(@RequestParam(required = false) String userId) {
        List<TestResult> results;
        if (userId != null && !userId.isEmpty()) {
            results = testResultRepository.findByUserId(userId);
        } else {
            results = testResultRepository.findAll();
        }
        return new ResponseEntity<>(results, HttpStatus.OK);
    }

    // --- FEEDBACK ---

    @PostMapping("/feedback")
    public ResponseEntity<Feedback> saveFeedback(@RequestBody Feedback feedback) {
        if (feedback.getResultId() == null) {
            throw new IllegalArgumentException("resultId is required");
        }

        Optional<Feedback> existing = feedbackRepository.findByResultId(feedback.getResultId());
        
        if (existing.isPresent()) {
            Feedback f = existing.get();
            f.setFeedbackText(feedback.getFeedbackText());
            f.setSuggestions(feedback.getSuggestions());
            f.setDate(feedback.getDate());
            return new ResponseEntity<>(feedbackRepository.save(f), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(feedbackRepository.save(feedback), HttpStatus.CREATED);
        }
    }

    @GetMapping("/feedback")
    public ResponseEntity<List<Feedback>> getAllFeedback() {
        return new ResponseEntity<>(feedbackRepository.findAll(), HttpStatus.OK);
    }

    @GetMapping("/feedback/result/{resultId}")
    public ResponseEntity<?> getFeedbackByResult(@PathVariable Long resultId) {
        Optional<Feedback> existing = feedbackRepository.findByResultId(resultId);
        if (existing.isPresent()) {
            return new ResponseEntity<>(existing.get(), HttpStatus.OK);
        } else {
            throw new ResourceNotFoundException("Feedback not found for result id " + resultId);
        }
    }
}
