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

    @JsonProperty("custom_hours")
    private Double customHours = 0.0;
}
