package com.oauth.demo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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

//    public String getUserCommits(String userName, String accessToken) {
//        HttpHeaders headers = new HttpHeaders();
//        headers.setBearerAuth(accessToken);
//        HttpEntity<Void> request = new HttpEntity<>(headers);
//
//        // 모든 리포지토리 목록을 가져옵니다.
//        ResponseEntity<Repository[]> reposResponse = restTemplate.exchange(
//            "https://api.github.com/user/repos?visibility=all&affiliation=owner,collaborator",
//            HttpMethod.GET,
//            request,
//            Repository[].class
//        );
//
//        // 날짜별 커밋 횟수를 저장할 맵 (TreeMap을 사용하여 날짜별로 정렬)
//        Map<LocalDate, Integer> commitCountByDate = new TreeMap<>();
////        for (Repository repo : reposResponse.getBody()) {
////            String repoName = repo.getName();
////            ResponseEntity<Commit[]> commitsResponse = restTemplate.exchange(
////                    COMMITS_URL,
////                    HttpMethod.GET,
////                    request,
////                    Commit[].class,
////                    userName, repoName    // 이부분 이렇게 처리하면 에러뜸, 하나의 리스트로 처리
////            );
////
////            for (Commit commit : commitsResponse.getBody()) {
////                allCommits.append("[").append(repoName).append("] ")
////                        .append(commit.getCommit().getMessage()).append("\n");
////            }
////
////            System.out.println(allCommits);
////        }
//        // 각 리포지토리의 커밋 내역을 가져옵니다.
//        // 각 리포지토리의 커밋 내역을 가져옵니다.
//        for (Repository repo : reposResponse.getBody()) {
//            String repoName = repo.getName();
//            String ownerName = repo.getOwner().getLogin();
//            Map<String, String> uriVariables = new HashMap<>();
//            uriVariables.put("owner", ownerName);
//            uriVariables.put("repo", repoName);
//
//            int page = 1;
//            boolean hasMoreCommits = true;
//
//            while (hasMoreCommits) {
//                try {
//                    ResponseEntity<Commit[]> commitsResponse = restTemplate.exchange(
//                        COMMITS_URL + "?page=" + page + "&per_page=100",
//                        HttpMethod.GET,
//                        request,
//                        Commit[].class,
//                        uriVariables
//                    );
//
//                    Commit[] commits = commitsResponse.getBody();
//                    if (commits == null || commits.length == 0) {
//                        hasMoreCommits = false;
//                    } else {
//                        for (Commit commit : commits) {
//                            String commitDateStr = commit.getCommit().getCommitter().getDate();
//                            OffsetDateTime commitDateTime = OffsetDateTime.parse(commitDateStr);
//                            LocalDate commitDate = commitDateTime.toLocalDate();
//                            commitCountByDate.put(commitDate, commitCountByDate.getOrDefault(commitDate, 0) + 1);
//                        }
//                        page++;
//                    }
//                    Thread.sleep(1000);
//                } catch (HttpClientErrorException e) {
//                    if (e.getStatusCode() == HttpStatus.CONFLICT && e.getResponseBodyAsString().contains("Git Repository is empty")) {
//                        System.err.println("Repository is empty: " + repoName);
//                    } else {
//                        System.err.println("Error fetching commits for repository " + repoName + ": " + e.getMessage());
//                    }
//                    hasMoreCommits = false;
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                }
//            }
//
//
//        }
//
//        // 리포지토리 정보 및 커밋 데이터를 포함한 결과 생성
//        StringBuilder result = new StringBuilder();
//        for (Map.Entry<LocalDate, Integer> entry : commitCountByDate.entrySet()) {
//            result.append(entry.getKey()).append(": ").append(entry.getValue()).append(" commits\n");
//        }
//        return result.toString();
//    }

    public String getUserCommits(String userName, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // 모든 리포지토리 목록을 가져옴 (visibility=all과 affiliation=owner,collaborator 추가)
        ResponseEntity<Repository[]> reposResponse = restTemplate.exchange(
            "https://api.github.com/user/repos?visibility=all&affiliation=owner,collaborator", // private 리포지토리 포함
            HttpMethod.GET,
            request,
            Repository[].class
        );


        // 날짜별 커밋 횟수를 저장할 맵 (TreeMap을 사용하여 날짜별로 정렬)
        Map<LocalDate, Integer> commitCountByDate = new TreeMap<>();
        // 각 리포지토리의 커밋 내역을 가져옵니다.
        for (Repository repo : reposResponse.getBody()) {
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
                            String commitDateStr = commit.getCommit().getCommitter().getDate();
                            OffsetDateTime commitDateTime = OffsetDateTime.parse(commitDateStr);
                            LocalDate commitDate = commitDateTime.toLocalDate();
                            commitCountByDate.put(commitDate, commitCountByDate.getOrDefault(commitDate, 0) + 1);
                        }
                        page++;
                    }
                    // 요청 간 딜레이 추가 (rate limit 대응)
                    Thread.sleep(1000);
                } catch (HttpClientErrorException e) {
                    if (e.getStatusCode() == HttpStatus.CONFLICT && e.getResponseBodyAsString().contains("Git Repository is empty")) {
                        System.err.println("Repository is empty: " + repoName);
                    } else {
                        System.err.println("Error fetching commits for repository " + repoName + ": " + e.getMessage());
                    }
                    hasMoreCommits = false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        StringBuilder result = new StringBuilder();
        for (Map.Entry<LocalDate, Integer> entry : commitCountByDate.entrySet()) {
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
        @JsonProperty("commit")
        private CommitDetails commit;

        @NoArgsConstructor
        @Getter
        public static class CommitDetails {
            @JsonProperty("message")
            private String message;

            @JsonProperty("committer")
            private Committer committer;

            @NoArgsConstructor
            @Getter
            public static class Committer {
                @JsonProperty("date")
                private String date;
            }
        }
    }
}
