package edu.nmamit.chainvax.site;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A custody location: district store, transport van or health centre. */
@Entity
@Table(name = "site")
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SiteType type;

    @Column(nullable = false, length = 80)
    private String district;

    protected Site() {
    }

    public Site(String code, String name, SiteType type, String district) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.district = district;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public SiteType getType() {
        return type;
    }

    public String getDistrict() {
        return district;
    }
}
