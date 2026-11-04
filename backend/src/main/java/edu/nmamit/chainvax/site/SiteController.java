package edu.nmamit.chainvax.site;

import java.util.List;

import edu.nmamit.chainvax.common.Access;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sites")
@PreAuthorize(Access.STAFF)
@Tag(name = "Reference data")
public class SiteController {

    private final SiteRepository sites;

    public SiteController(SiteRepository sites) {
        this.sites = sites;
    }

    public record SiteView(Long id, String code, String name, String type, String district) {
    }

    @GetMapping
    public List<SiteView> list() {
        return sites.findAllByOrderByIdAsc().stream()
                .map(s -> new SiteView(s.getId(), s.getCode(), s.getName(), s.getType().name(), s.getDistrict()))
                .toList();
    }
}
