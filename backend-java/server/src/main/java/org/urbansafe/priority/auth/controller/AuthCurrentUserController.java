package org.urbansafe.priority.auth.controller;

import java.util.UUID;
import org.urbansafe.priority.auth.converter.AuthConverter;
import org.urbansafe.priority.auth.result.CurrentUserResult;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.auth.service.AuthService;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.model.api.AuthCurrentUserApi;
import org.urbansafe.priority.model.dto.CurrentUserResponse;
import org.urbansafe.priority.model.dto.CurrentUserSuccessResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthCurrentUserController implements AuthCurrentUserApi {

    private final AuthService authService;

    public AuthCurrentUserController(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<CurrentUserSuccessResponse> getCurrentUser() {
        UUID userId = CurrentUser.getUserId();
        CurrentUserResult currentUserResult = authService.getCurrentUser(userId);
        CurrentUserResponse userResponse = AuthConverter.toCurrentUserResponse(currentUserResult);

        ResponseMetadata metadata = ResponseMetadataFactory.success();
        CurrentUserSuccessResponse response = new CurrentUserSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(userResponse);
        response.setError(null);

        return ResponseEntity.ok(response);
    }
}
