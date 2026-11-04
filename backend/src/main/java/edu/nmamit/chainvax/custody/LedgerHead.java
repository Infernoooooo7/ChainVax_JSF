package edu.nmamit.chainvax.custody;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Single-row pointer to the newest ledger entry; row-locked to serialise appends. */
@Entity
@Table(name = "ledger_head")
public class LedgerHead {

    @Id
    private Integer id;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    @Column(name = "last_hash", nullable = false, length = 64)
    private String lastHash;

    protected LedgerHead() {
    }

    void advance(long seq, String hash) {
        this.lastSeq = seq;
        this.lastHash = hash;
    }

    public Integer getId() {
        return id;
    }

    public long getLastSeq() {
        return lastSeq;
    }

    public String getLastHash() {
        return lastHash;
    }
}
