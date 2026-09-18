package com.example.sso.repository;

import com.example.sso.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndProvider(String email, String provider);

    Optional<User> findByProviderUserId(String providerUserId);

    List<User> findAllByOrderByLastLoginAtDesc();
}
