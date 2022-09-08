package io.metersphere.track.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Comparator;

@Setter
@Getter
public class DemandDTO {
    private String id;
    private String name;
    private String platform;
    private String title;
    private String product;
    private String platformStatus;
    private String description;
    private String stage;
    private String plan;
    private String planName;
    private String module;
}
