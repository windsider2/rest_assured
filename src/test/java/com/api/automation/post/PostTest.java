package com.api.automation.post;

import com.api.automation.BaseTest;
import com.api.automation.dataprovider.PostDataProvider;
import com.api.automation.model.Post;
import io.restassured.http.Headers;
import io.restassured.response.ValidatableResponse;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.IntStream;

import static com.api.automation.dataprovider.PostDataProvider.*;
import static com.api.automation.utils.ConfigSetter.*;
import static io.restassured.module.jsv.JsonSchemaValidator.*;
import static java.time.format.DateTimeFormatter.*;
import static java.time.temporal.ChronoUnit.*;
import static org.apache.http.HttpStatus.*;
import static org.hamcrest.Matchers.*;

public class PostTest extends BaseTest {
    private static final String PATH_TO_POSTS = getConfigValue("post_path").toString();
    private static final String POST_ID_FORMATTER = "/{postId}";

    @Test
    public void verifyCertificateTest() {
        restClient.buildRequest()
                .given()
                .relaxedHTTPSValidation()
                .when()
                .get(getConfigValue("base_url").toString());
    }

    @Test
    public void verifyPostsSchemaTest() {
        //the test verifies status code and schema of all returned posts
        restClient.buildRequest().get(PATH_TO_POSTS)
                .then()
                .assertThat()
                .statusCode(SC_OK)
                .body(matchesJsonSchemaInClasspath("posts-schema.json"));
    }

    @Test
    public void verifyGetPostsResponseTimeTest() {
        //the test verifies status code and response time
        restClient.buildRequest()
                .get(PATH_TO_POSTS)
                .then()
                .assertThat()
                .statusCode(SC_OK)
                .time(lessThan(1000L));
    }

    @Test(dataProvider = "postProvider", dataProviderClass = PostDataProvider.class)
    public void getPostObjectDataTest(int postId, int statusCode, Post expectedPost) {
        //the test verifies status code and data of a returned post object
        //to set data for positive (with existing id) and negative (with not existing id) cases data provider is used
        executeNumberOfGetRequestsAndVerifyResponseData(1, postId, statusCode, expectedPost);
    }

    @Test(dataProvider = "postProviderForIdempotence", dataProviderClass = PostDataProvider.class)
    public void verifyIdempotenceTest(int postId, int statusCode, Post expectedPost) {
        //the test verifies there is no state change in the system (idempotence) after some the same requests
        final int numberOfRequests = 2;
        executeNumberOfGetRequestsAndVerifyResponseData(numberOfRequests, postId, statusCode, expectedPost);
    }

    @Test(dataProvider = "postBodyProvider", dataProviderClass = PostDataProvider.class)
    public void postPostObjectTest(String path, Post bodyToPost, int statusCode, String expectedResponseBody) {
        //the test verifies status code and returned body after executing post request
        //in negative case wrong path is used
        //the test fails after get request of the updated resource as application under test does not allow to create objects
        final int postId = bodyToPost.getId();
        final String expectedBody = generateSerializedPost(bodyToPost);
        final String response = restClient
                .buildRequest()
                .body(expectedBody)
                .post(PATH_TO_POSTS + path)
                .then()
                .assertThat()
                .statusCode(statusCode)
                .extract().asPrettyString();
        Assert.assertEquals(response.replaceAll("\\s", ""), expectedResponseBody);
        //for positive test verification of created resource with get request
        if (statusCode == SC_CREATED) {
            final String actualResponseBody = restClient.buildRequest()
                    .get(PATH_TO_POSTS + POST_ID_FORMATTER, postId)
                    .then()
                    .assertThat()
                    .statusCode(SC_OK)
                    .extract()
                    .asPrettyString();
            Assert.assertEquals(actualResponseBody.replaceAll("\\s", ""), expectedBody, "Returned body after post request does not match the expected");
        }
    }

    @Test(dataProvider = "putBodyProvider", dataProviderClass = PostDataProvider.class)
    public void putPostObjectTest(int postId, String bodyToPost, int statusCode, String expectedResponseBody) {
        //the test verifies status code and returned body after executing put request
        //in negative case not existing post id is used
        //the test fails after get request of the updated resource as application under test does not allow to update objects
        final String response = restClient
                .buildRequest()
                .body(bodyToPost)
                .put(PATH_TO_POSTS + POST_ID_FORMATTER, postId)
                .then()
                .assertThat()
                .statusCode(statusCode)
                .extract().asString();
        Assert.assertTrue(response.contains(expectedResponseBody));
        //for positive test verification of updated resource with get request
        if (statusCode == SC_OK) {
            final String getResponseBody = restClient.buildRequest()
                    .get(PATH_TO_POSTS + POST_ID_FORMATTER, postId)
                    .then()
                    .assertThat()
                    .statusCode(SC_OK)
                    .extract()
                    .asString();
            Assert.assertEquals(getResponseBody.replaceAll("\\s", ""), bodyToPost, "Returned body after put request does not match the expected");
        }
    }

    @Test
    public void patchPostObjectTest() {
        //the test verifies status code and returned body after executing put request
        //in negative case not existing post id is used
        //the test fails as application under test does not allow to update resources
        final int postId = 1;
        final ValidatableResponse response = restClient
                .buildRequest()
                .body("{\"title\": \"updated title\"}")
                .patch(PATH_TO_POSTS + POST_ID_FORMATTER, postId)
                .then()
                .assertThat()
                .statusCode(SC_OK)
                .body("title", equalTo("updated title"));
    }

    @Test
    public void deletePostObjectTest() {
        //the test verifies status code and returned body after executing put request
        //in negative case not existing post id is used
        //the test fails as application under test does not allow to update resources
        final int postId = 1;
        final String response = restClient
                .buildRequest()
                .delete(PATH_TO_POSTS + POST_ID_FORMATTER, postId)
                .then()
                .assertThat()
                .statusCode(SC_OK)
                .extract().asString();

        Assert.assertEquals(response, "{}", "Response body after delete request does not match the expected");


    }

    @Test
    public void verifyGetPostResponseTimeTest() {
        //the test verifies status code and response time of last post
        final long size = restClient.buildRequest()
                .get(PATH_TO_POSTS)
                .then()
                .assertThat()
                .statusCode(SC_OK)
                .extract().as(Post[].class)
                .length;

        restClient.buildRequest()
                .get(PATH_TO_POSTS + "/" + size)
                .then()
                .assertThat()
                .statusCode(SC_OK)
                .time(lessThan(1000L));
    }

    @Test
    public void verifyHeadersTest() {
        Headers headers = restClient.buildRequest()
                .header("Server-Timing", "cf-q-config")
                .get(PATH_TO_POSTS)
                .then()
                .assertThat()
                .statusCode(SC_OK)
                .extract().headers();
        final String actualDate = headers.get("Date").getValue();
        final String expectedDate = ZonedDateTime.now(ZoneId.of("GMT")).truncatedTo(SECONDS).format(RFC_1123_DATE_TIME);
        final String actualContentType = headers.get("Content-Type").getValue();
        final String actualConnection = headers.get("Connection").getValue();
        SoftAssert soft = new SoftAssert();
        soft.assertEquals(actualDate, expectedDate, "Date header value does not match the expected");
        soft.assertEquals(actualContentType, "application/json; charset=utf-8", "Content Header type value does not match the expected");
        soft.assertEquals(actualConnection, "keep-alive", "Connection Header value does not match the expected");
        soft.assertAll();
    }

    private void executeNumberOfGetRequestsAndVerifyResponseData(int numberOfExecutions, int postId, int statusCode, Post expectedPost) {
        IntStream.range(0, numberOfExecutions).forEach(request ->
                {
                    final Post actualPost = restClient.buildRequest()
                            .get(PATH_TO_POSTS + POST_ID_FORMATTER, postId)
                            .then()
                            .assertThat()
                            .statusCode(statusCode)
                            .extract().as(Post.class);
                    Assert.assertEquals(actualPost, expectedPost, "Post with id " + postId + " does not match the expected");
                }
        );
    }
}
