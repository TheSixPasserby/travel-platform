package com.example.travelplatform.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.example.travelplatform.common.api.ApiResponse;
import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import com.example.travelplatform.config.AppConfig;
import com.example.travelplatform.security.AuthWebConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实 MockMvc 测试（Web 切片，无数据库、无 Spring Boot 全上下文）。
 * 只装配测试类内定义的 ErrorProbeController 与生产 GlobalExceptionHandler，
 * 并使用应用自身 application.yml 配置的 Jackson（拒绝未知字段、拒绝标量隐式转换）。
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ErrorProbeController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AuthWebConfig.class))
@Import({GlobalExceptionHandlerTest.ErrorProbeController.class, AppConfig.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** 测试专用 Controller，不进入生产代码。 */
    @RestController
    @RequestMapping("/api/v1/test-errors")
    static class ErrorProbeController {

        @PostMapping("/validation")
        ApiResponse<Void> validation(@Valid @RequestBody ValidationRequest request) {
            return ApiResponse.success(null);
        }

        @PostMapping("/business-conflict")
        ApiResponse<Void> businessConflict() {
            throw new ApiException(ErrorCode.DUPLICATE_KEY, "用户名已被占用");
        }

        @PostMapping("/unauthorized")
        ApiResponse<Void> unauthorized() {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        @PostMapping("/forbidden")
        ApiResponse<Void> forbidden() {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        @PostMapping("/not-found")
        ApiResponse<Void> notFound() {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        @PostMapping("/not-login")
        ApiResponse<Void> notLogin() {
            throw NotLoginException.newInstance("login", NotLoginException.NOT_TOKEN, null, null);
        }

        @PostMapping("/not-role")
        ApiResponse<Void> notRole() {
            throw new NotRoleException("admin");
        }

        @PostMapping("/not-permission")
        ApiResponse<Void> notPermission() {
            throw new NotPermissionException("user:delete");
        }

        @PostMapping("/duplicate-key")
        ApiResponse<Void> duplicateKey() {
            throw new DuplicateKeyException("Duplicate entry 'alice' for key 'users.uk_users_username'");
        }

        @PostMapping("/unexpected")
        ApiResponse<Void> unexpected() {
            throw new IllegalStateException(
                    "SELECT password_hash FROM users WHERE username='alice' at /Users/secret/app/UserMapper.java");
        }

        record ValidationRequest(@NotBlank String username, @Size(max = 5) String nickname) {
        }

        @PostMapping("/ignored-field")
        ApiResponse<Void> ignoredField(@RequestBody IgnoredRequest request) {
            return ApiResponse.success(null);
        }

        record IgnoredRequest(String username, @JsonIgnore boolean nicknamePresent) {
        }

        @PostMapping("/write-conflict")
        ApiResponse<Void> writeConflict() {
            throw new CannotAcquireLockException("internal SQL lock details");
        }

        @PostMapping("/integer")
        ApiResponse<Void> integer(@RequestBody IntegerRequest request) { return ApiResponse.success(null); }

        record IntegerRequest(Integer sortOrder) { }
    }

    @Test
    @DisplayName("参数校验失败：400 / 40001 且 data.fieldErrors 含字段与消息，不回显原始被拒绝值")
    void validationFailureReturns40001WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"nickname\":\"toolong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data.fieldErrors").isArray())
                .andExpect(jsonPath("$.data.fieldErrors[?(@.field=='username')]").exists())
                .andExpect(jsonPath("$.data.fieldErrors[?(@.field=='nickname')]").exists())
                .andExpect(jsonPath("$.data.fieldErrors[0].message").isString())
                .andExpect(content().string(not(containsString("toolong"))));
    }

    @Test
    @DisplayName("未知 JSON 字段：400 / 40001，不静默忽略")
    void unknownJsonFieldReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(content().string(not(containsString("ADMIN"))));
    }

    @Test
    void ignoredInternalFieldCannotBeInjected() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/ignored-field")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"nicknamePresent\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test void transientDatabaseLockReturnsStateConflict() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/write-conflict"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40902))
                .andExpect(content().string(not(containsString("SQL"))));
    }

    @Test void decimalCannotBeSilentlyTruncatedToInteger() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/integer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortOrder\":1.5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void unsupportedMethodKeeps405WithErrorEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/validation"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(content().string(containsString("\"data\":null")));
    }

    @Test
    void unacceptableResponseTypeKeeps406WithErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/ignored-field")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}")
                        .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(content().string(containsString("\"data\":null")));
    }

    @Test
    @DisplayName("数字/布尔到字符串的隐式转换被拒绝：400 / 40001")
    void scalarToTextCoercionReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":12345}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(post("/api/v1/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("业务异常：409 / 40901、保留业务消息、data 为 null 且字段保留")
    void businessExceptionReturns40901() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/business-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value("用户名已被占用"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(containsString("\"data\":null")));
    }

    @Test
    @DisplayName("Sa-Token 未登录 40101、缺角色/缺权限 40301，均不回显角色或权限串")
    void saTokenExceptionsMapTo40x() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/not-login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(post("/api/v1/test-errors/not-role"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301))
                .andExpect(content().string(not(containsString("admin"))));

        mockMvc.perform(post("/api/v1/test-errors/not-permission"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301))
                .andExpect(content().string(not(containsString("user:delete"))));
    }

    @Test
    @DisplayName("唯一键冲突：409 / 40901，不泄漏索引名等数据库细节")
    void duplicateKeyExceptionReturns40901() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/duplicate-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(content().string(not(containsString("uk_users_username"))));
    }

    @Test
    @DisplayName("未登录 / 权限不足 / 资源不存在 分别返回 40101 / 40301 / 40401")
    void standardErrorCodesMapToHttpStatus() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(post("/api/v1/test-errors/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(post("/api/v1/test-errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("任意内部异常：500 / 50001，响应不含原始 SQL、内部路径与异常类名")
    void unexpectedExceptionReturns50001WithoutInternals() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(content().string(not(containsString("SELECT password_hash"))))
                .andExpect(content().string(not(containsString("/Users/secret"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    @DisplayName("响应包契约：ApiResponse 成功/失败工厂与 JSON 保留 data:null")
    void apiResponseEnvelopeContract() throws Exception {
        ApiResponse<Void> success = ApiResponse.success(null);
        assertThat(success.getCode()).isZero();
        assertThat(success.getMessage()).isEqualTo("success");
        assertThat(success.getData()).isNull();

        ApiResponse<Object> failure = ApiResponse.failure(40901, "冲突", null);
        assertThat(failure.getCode()).isEqualTo(40901);
        assertThat(failure.getMessage()).isEqualTo("冲突");
        assertThat(failure.getData()).isNull();

        assertThat(objectMapper.writeValueAsString(success)).contains("\"code\":0", "\"message\":\"success\"", "\"data\":null");
    }

    @Test
    @DisplayName("ErrorCode 契约：整数码、HTTP 状态与通用消息（前端与后续任务依赖）")
    void errorCodeContract() {
        assertThat(ErrorCode.VALIDATION_FAILED.getCode()).isEqualTo(40001);
        assertThat(ErrorCode.VALIDATION_FAILED.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.UNAUTHORIZED.getCode()).isEqualTo(40101);
        assertThat(ErrorCode.UNAUTHORIZED.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.BAD_CREDENTIALS.getCode()).isEqualTo(40102);
        assertThat(ErrorCode.BAD_CREDENTIALS.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.FORBIDDEN.getCode()).isEqualTo(40301);
        assertThat(ErrorCode.FORBIDDEN.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.NOT_FOUND.getCode()).isEqualTo(40401);
        assertThat(ErrorCode.NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.DUPLICATE_KEY.getCode()).isEqualTo(40901);
        assertThat(ErrorCode.DUPLICATE_KEY.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.STATE_CONFLICT.getCode()).isEqualTo(40902);
        assertThat(ErrorCode.STATE_CONFLICT.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.PAYLOAD_TOO_LARGE.getCode()).isEqualTo(41301);
        assertThat(ErrorCode.PAYLOAD_TOO_LARGE.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode()).isEqualTo(41501);
        assertThat(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(ErrorCode.INTERNAL_ERROR.getCode()).isEqualTo(50001);
        assertThat(ErrorCode.INTERNAL_ERROR.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ErrorCode.INTERNAL_ERROR.getMessage())
                .isNotBlank()
                .doesNotContain("Exception", "SQL", "/Users", "password");
    }
}
