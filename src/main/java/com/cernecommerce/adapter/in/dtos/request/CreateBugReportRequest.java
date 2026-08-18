package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateBugReportRequest {
    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 5000)
    private String description;

    @Size(max = 500)
    private String pageUrl;

    @Size(max = 500)
    private String userAgent;
}
