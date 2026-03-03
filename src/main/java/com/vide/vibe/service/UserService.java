package com.vide.vibe.service;

import com.vide.vibe.model.User;
import com.vide.vibe.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    @Transactional
    public User create(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already in use: " + user.getEmail());
        }
        user.setStatus(User.Status.PENDING);
        return userRepository.save(user);
    }

    @Transactional
    public User update(UUID id, User updated) {
        User existing = findById(id);
        existing.setEmail(updated.getEmail());
        existing.setRole(updated.getRole());
        existing.setStatus(updated.getStatus());
        return userRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        User user = findById(id);
        user.softDelete();
        userRepository.save(user);
    }
}