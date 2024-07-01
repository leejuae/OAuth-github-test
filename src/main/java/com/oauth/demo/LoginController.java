package com.oauth.demo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

@Controller
@RequiredArgsConstructor
public class LoginController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${github.clientId}")
    private String clientId;

    @Value("${github.clientSecret}")
    private String clientSecret;

    private static final String ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String MEMBER_INFO_URL = "https://api.github.com/user";

    @GetMapping("/callback")
    public ResponseEntity<String> getUserInfo(@RequestParam String code) {
        String accessToken = getAccessToken(code);
        String userName = getUserName(accessToken);
        return ResponseEntity.ok(userName);
    }

    private String getAccessToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");

        OAuthAccessTokenRequest tokenRequest = new OAuthAccessTokenRequest(clientId, clientSecret, code);
        HttpEntity<OAuthAccessTokenRequest> request = new HttpEntity<>(tokenRequest, headers);

        return restTemplate.postForObject(
                ACCESS_TOKEN_URL,
                request,
                OAuthAccessTokenResponse.class
        ).getAccessToken();
    }

    public String getUserName(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        return restTemplate.exchange(
                MEMBER_INFO_URL,
                HttpMethod.GET,
                request,
                OAuthMemberInfoResponse.class
        ).getBody().getName();
    }

    @NoArgsConstructor
    @Getter
    public static class OAuthAccessTokenRequest {
        @JsonProperty("client_id")
        private String clientId;

        @JsonProperty("client_secret")
        private String clientSecret;

        @JsonProperty("code")
        private String code;

        public OAuthAccessTokenRequest(String clientId, String clientSecret, String code) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.code = code;
        }
    }

    @NoArgsConstructor
    @Getter
    public static class OAuthAccessTokenResponse {
        @JsonProperty("access_token")
        private String accessToken;
    }

    @NoArgsConstructor
    @Getter
    public static class OAuthMemberInfoResponse {
        @JsonProperty("login")
        private String name;
    }
}

