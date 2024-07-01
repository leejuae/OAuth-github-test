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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

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
    private static final String REPOS_URL = "https://api.github.com/user/repos";
    private static final String COMMITS_URL = "https://api.github.com/repos/{owner}/{repo}/commits";

    @GetMapping("/callback")
    public ResponseEntity<String> getUserInfo(@RequestParam String code) {
        String accessToken = getAccessToken(code);
        String userName = getUserName(accessToken);
        String commits = getUserCommits(userName, accessToken);
        return ResponseEntity.ok(commits);
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

    public String getUserCommits(String userName, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Repository[]> reposResponse = restTemplate.exchange(
                "https://api.github.com/user/repos?visibility=all&affiliation=owner,collaborator",
                HttpMethod.GET,
                request,
                Repository[].class
        );

        Map<Integer, Integer> commitCountByYear = new TreeMap<>();
        Set<String> seenCommitShas = new HashSet<>();

        for (Repository repo : reposResponse.getBody()) {
            if (repo.isFork()) {
                continue;
            }

            String repoName = repo.getName();
            String ownerName = repo.getOwner().getLogin();
            Map<String, String> uriVariables = new HashMap<>();
            uriVariables.put("owner", ownerName);
            uriVariables.put("repo", repoName);

            int page = 1;
            boolean hasMoreCommits = true;

            while (hasMoreCommits) {
                try {
                    ResponseEntity<Commit[]> commitsResponse = restTemplate.exchange(
                            COMMITS_URL + "?page=" + page + "&per_page=100",
                            HttpMethod.GET,
                            request,
                            Commit[].class,
                            uriVariables
                    );

                    Commit[] commits = commitsResponse.getBody();
                    if (commits == null || commits.length == 0) {
                        hasMoreCommits = false;
                    } else {
                        for (Commit commit : commits) {
                            String commitSha = commit.getSha();
                            if (!seenCommitShas.contains(commitSha)) {
                                seenCommitShas.add(commitSha);
                                String commitDateStr = commit.getCommit().getCommitter().getDate();
                                OffsetDateTime commitDateTime = OffsetDateTime.parse(commitDateStr);
                                int commitYear = commitDateTime.getYear();

                                // 커밋의 committer 또는 author가 현재 사용자와 일치하는지 확인
                                if (userName.equals(commit.getCommit().getCommitter().getName()) ||
                                        userName.equals(commit.getCommit().getAuthor().getName())) {
                                    commitCountByYear.put(commitYear, commitCountByYear.getOrDefault(commitYear, 0) + 1);
                                }
                            }
                        }
                        page++;
                    }
                } catch (HttpClientErrorException e) {
                    System.err.println("Error fetching commits for repository " + repoName + " on page " + page + ": " + e.getMessage());
                    hasMoreCommits = false;
                } catch (Exception e) {
                    System.err.println("Unexpected error fetching commits for repository " + repoName + " on page " + page + ": " + e.getMessage());
                    hasMoreCommits = false;
                }
            }
        }

        StringBuilder result = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : commitCountByYear.entrySet()) {
            result.append(entry.getKey()).append(": ").append(entry.getValue()).append(" commits\n");
        }

        return result.toString();
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

    @NoArgsConstructor
    @Getter
    public static class Repository {
        @JsonProperty("name")
        private String name;

        @JsonProperty("owner")
        private Owner owner;

        @JsonProperty("fork")
        private boolean isFork;

        @NoArgsConstructor
        @Getter
        public static class Owner {
            @JsonProperty("login")
            private String login;
        }
    }

    @NoArgsConstructor
    @Getter
    public static class Commit {
        @JsonProperty("sha")
        private String sha;

        @JsonProperty("commit")
        private CommitDetails commit;

        @NoArgsConstructor
        @Getter
        public static class CommitDetails {
            @JsonProperty("message")
            private String message;

            @JsonProperty("committer")
            private Committer committer;

            @JsonProperty("author")
            private Author author;

            @NoArgsConstructor
            @Getter
            public static class Committer {
                @JsonProperty("date")
                private String date;

                @JsonProperty("name")
                private String name;
            }

            @NoArgsConstructor
            @Getter
            public static class Author {
                @JsonProperty("date")
                private String date;

                @JsonProperty("name")
                private String name;
            }
        }
    }
}

