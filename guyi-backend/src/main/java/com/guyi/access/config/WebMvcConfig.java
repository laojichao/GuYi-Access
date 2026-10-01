package com.guyi.access.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the built Vue admin UI from the same JAR (deployment Option A).
 *
 * <p>The frontend is a history-mode SPA, so a deep link such as {@code /cards} has no file behind it
 * and must fall through to {@code index.html}. {@code /**} resource mapping does NOT shadow the REST
 * controllers: {@code @RestController} mappings are matched by a higher-priority handler mapping.
 *
 * <p>If {@code static/index.html} is absent (the frontend was not copied into the jar), the resolver
 * returns null and unknown paths behave exactly as before (404).
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // SPA fallback: only for extension-less paths, so a missing asset still 404s
                        if (!resourcePath.contains(".")) {
                            Resource index = new ClassPathResource("static/index.html");
                            if (index.exists() && index.isReadable()) {
                                return index;
                            }
                        }
                        return null;
                    }
                });
    }
}
