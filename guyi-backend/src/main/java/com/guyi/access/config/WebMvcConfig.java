package com.guyi.access.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.Set;

/**
 * Serves the built Vue admin UI from the same JAR (deployment Option A).
 *
 * <p>The frontend is a history-mode SPA, so a deep link such as {@code /cards} has no file behind it
 * and must fall through to {@code index.html}. {@code /**} resource mapping does NOT shadow the REST
 * controllers: {@code @RestController} mappings are matched by a higher-priority handler mapping.
 *
 * <p>If {@code static/index.html} is absent (the frontend was not copied into the jar), the resolver
 * returns null and unknown paths behave exactly as before (404).
 *
 * <p>Paths under {@code /api/} are deliberately excluded from the fallback: an unknown API path must
 * answer 404 (as JSON, via {@link ApiErrorController}) rather than hand the caller an HTML page with
 * HTTP 200 - which is exactly what happened before this guard existed.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** Prefixes that are never answered with the SPA shell, even when it exists. */
    private static final Set<String> NON_SPA_PREFIXES = Set.of("api/", "error");

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
                        // SPA fallback: only for extension-less, non-API paths, so a missing asset or
                        // an unknown endpoint still 404s instead of returning the app shell
                        if (!resourcePath.contains(".") && !isNonSpaPath(resourcePath)) {
                            Resource index = new ClassPathResource("static/index.html");
                            if (index.exists() && index.isReadable()) {
                                return index;
                            }
                        }
                        return null;
                    }
                });
    }

    private static boolean isNonSpaPath(String resourcePath) {
        // The resolver receives the path with a leading slash ("/api/..."), but be tolerant of both
        String normalized = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        return NON_SPA_PREFIXES.stream().anyMatch(normalized::startsWith);
    }
}
