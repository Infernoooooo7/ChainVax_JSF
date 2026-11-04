package edu.nmamit.chainvax.digest;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertDigestRepository extends JpaRepository<AlertDigest, Long> {

    List<AlertDigest> findTop20ByOrderByIdDesc();

    Optional<AlertDigest> findTopByOrderByIdDesc();
}
