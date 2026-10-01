package com.guyi.access.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GenerateCardsRequest {
    @JsonProperty("app_id")
    private Integer appId;

    private String type = "day";
    private Integer num = 1;
    private String pre = "";
    private String note = "API接口批量生卡";

    // Must stay null when the caller does not send custom_hours: a non-null default of 0.0 made the
    // controller's "> 0" validation reject every standard card type (hour/day/week/month/...).
    @JsonProperty("custom_hours")
    private Double customHours;
}
