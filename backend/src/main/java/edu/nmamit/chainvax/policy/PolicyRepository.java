package edu.nmamit.chainvax.policy;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<ExcursionPolicy, Long> {

    Optional<ExcursionPolicy> findByCode(String code);

    List<ExcursionPolicy> findAllByOrderByIdAsc();
}
