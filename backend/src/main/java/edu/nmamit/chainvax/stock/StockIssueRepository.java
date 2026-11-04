package edu.nmamit.chainvax.stock;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockIssueRepository extends JpaRepository<StockIssue, Long> {

    List<StockIssue> findTop50ByOrderByIdDesc();

    List<StockIssue> findTop50BySiteCodeOrderByIdDesc(String siteCode);
}
