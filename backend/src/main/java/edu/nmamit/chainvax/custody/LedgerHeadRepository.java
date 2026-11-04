package edu.nmamit.chainvax.custody;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LedgerHeadRepository extends JpaRepository<LedgerHead, Integer> {

    /** SELECT ... FOR UPDATE on the head row: concurrent appends queue up instead of forking the chain. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from LedgerHead h where h.id = 1")
    LedgerHead lockHead();
}
