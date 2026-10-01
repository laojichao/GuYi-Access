package com.guyi.access.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class VerifyRequest {
    @JsonProperty("app_key")
    private String appKey;

    @JsonProperty("card_code")
    private String cardCode;

    private String card;

    @JsonProperty("device_hash")
    private String deviceHash;

    private String device;
    private String action = "verify";

    @JsonProperty("custom_data")
    private String customData;

    @JsonProperty("api_token")
    private String apiToken;

    @JsonProperty("app_id")
    private Integer appId;

    private Integer num = 1;
    private String type = "day";
    private String pre = "";
    private String note = "API接口批量生卡";

    // Must stay null when the caller does not send custom_hours: a non-null default of 0.0 made the
    // generate action's "> 0" validation reject every standard card type.
    @JsonProperty("custom_hours")
    private Double customHours;

    public String getEffectiveCardCode() {
        return cardCode != null && !cardCode.isEmpty() ? cardCode : card;
    }

    public String getEffectiveDevice() {
        return deviceHash != null && !deviceHash.isEmpty() ? deviceHash : device;
    }
}
