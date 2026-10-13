package com.dss.flash.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class FlashOrderDTO {
    @NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") private String productId;
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,128}") private String requestKey;
}
