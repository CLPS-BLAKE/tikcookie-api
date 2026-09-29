package com.dss.common.config;

import com.dss.common.web.InternalKeyInterceptor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc（Swagger）配置（已实现）：分"C 端接口"和"内部接口"两组，声明两种鉴权方式。
 * 需要登录的 C 端接口在 Controller 上标 @SecurityRequirement(name = BEARER_SCHEME)。
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerToken";
    public static final String INTERNAL_KEY_SCHEME = "internalKey";

    @Bean
    public OpenAPI dssOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("抖省省仿制练手 API")
                        .version("v1")
                        .description("统一响应 {code, msg, data}；骨架期所有业务接口返回 501 / 50100。详见 docs/接口文档.md"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("登录接口返回的 token"))
                        .addSecuritySchemes(INTERNAL_KEY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(InternalKeyInterceptor.HEADER)
                                .description("内部接口口令，对应配置 dss.internal.key")));
    }

    @Bean
    public GroupedOpenApi consumerApi() {
        return GroupedOpenApi.builder()
                .group("consumer")
                .displayName("C 端接口")
                .pathsToMatch("/api/v1/**")
                .pathsToExclude("/api/v1/internal/**")
                .build();
    }

    @Bean
    public GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .displayName("内部接口")
                .pathsToMatch("/api/v1/internal/**")
                .addOpenApiCustomizer(openApi -> openApi.addSecurityItem(new SecurityRequirement().addList(INTERNAL_KEY_SCHEME)))
                .build();
    }
}
