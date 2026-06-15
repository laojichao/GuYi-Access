package com.guyi.access.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchRequest {
    private List<Integer> ids;
    private Double hours;
}
