package com.guyi.access.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Installation payload. Database connectivity is configured through environment variables
 * (DB_URL / DB_USERNAME / DB_PASSWORD), never through this request - the removed dbHost/dbName/
 * dbUser/dbPass/dbPort fields were dead weight that wrongly suggested remote DB configuration.
 *
 * <p>JSON keys are snake_case, like every other endpoint of this API. Without the explicit
 * {@code @JsonProperty} Jackson would look for {@code adminPassword}, so a request carrying
 * {@code admin_password} left the field null and the install always failed its length check.
 * The camelCase spellings stay accepted as aliases.
 */
@Data
public class InstallRequest {

    @JsonProperty("admin_password")
    @JsonAlias("adminPassword")
    private String adminPassword;

    /**
     * Optional pre-shared install token. Only required when the deployment sets INSTALL_TOKEN; it
     * closes the window in which whoever reaches a freshly deployed instance first can claim the
     * administrator account.
     */
    @JsonProperty("install_token")
    @JsonAlias("installToken")
    private String installToken;
}
