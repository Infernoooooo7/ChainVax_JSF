package edu.nmamit.chainvax.admin;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the admin console (react-admin build in classpath:/admin-ui/admin) at /admin.
 * react-admin uses hash routing (/admin/#/users), so only the entry page needs a mapping.
 */
@Configuration
public class AdminConsoleWebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/admin").setViewName("redirect:/admin/");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
    }
}
