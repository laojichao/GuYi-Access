package com.guyi.access.dto;

import lombok.Data;

/**
 * Installation payload. Database connectivity is configured through environment variables
 * (DB_URL / DB_USERNAME / DB_PASSWORD), never through this request - the removed dbHost/dbName/
 * dbUser/dbPass/dbPort fields were dead weight that wrongly suggested remote DB configuration.
 */
@Data
public class InstallRequest {
    private String adminPassword;

    /**
     * Optional pre-shared install token (JSON key {@code install_token}). Only required when the
     * deployment sets INSTALL_TOKEN; it closes the window in which whoever reaches a freshly
     * deployed instance first can claim the administrator account.
     */
    @com.fasterxml.jackson.annotation.JsonProperty("install_token")
    private String installToken;
}
