package org.urbansafe.priority.auth.controller;

import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.auth.converter.AuthConverter;
import org.urbansafe.priority.auth.result.LoginResult;
import org.urbansafe.priority.auth.service.AuthService;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.model.api.AuthLoginApi;
import org.urbansafe.priority.model.dto.LoginSuccessResponse;
import org.urbansafe.priority.model.dto.LoginRequest;
import org.urbansafe.priority.model.dto.LoginResponse;
import org.urbansafe.priority.model.dto.LogoutSuccessResponse;
import org.urbansafe.priority.model.dto.LogoutResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthLoginController implements AuthLoginApi {

    private final AuthService authService;

    public AuthLoginController(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<LoginSuccessResponse> login(LoginRequest loginRequest) {
        LoginResult loginResult = authService.login(
                loginRequest.getUsername(),
                loginRequest.getPassword());
        LoginResponse loginResponse = AuthConverter.toLoginResponse(loginResult);

        ResponseMetadata metadata = ResponseMetadataFactory.success();
        LoginSuccessResponse response = new LoginSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(loginResponse);
        response.setError(null);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<LogoutSuccessResponse> logout() {
        authService.logout();
        ResponseMetadata metadata = ResponseMetadataFactory.success();

        LogoutResponse logoutData = new LogoutResponse();
        logoutData.setMessage("退出成功");

        LogoutSuccessResponse response = new LogoutSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(logoutData);
        response.setError(null);

        return ResponseEntity.ok(response);
    }
}
