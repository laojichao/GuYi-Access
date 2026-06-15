package com.guyi.access.dto;

import lombok.Data;

@Data
public class InstallRequest {
    private String dbHost = "127.0.0.1";
    private String dbName;
    private String dbUser;
    private String dbPass;
    private String dbPort = "3306";
    private String adminPassword;
}
