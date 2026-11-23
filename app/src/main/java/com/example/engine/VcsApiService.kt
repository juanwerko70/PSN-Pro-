package com.example.engine

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GitHubRepoInfo(
    val name: String = "",
    @Json(name = "full_name") val fullName: String = "",
    val description: String? = null,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "stargazers_count") val stars: Int = 0,
    @Json(name = "open_issues_count") val openIssues: Int = 0,
    @Json(name = "pushed_at") val pushedAt: String? = null,
    @Json(name = "html_url") val htmlUrl: String = ""
)

@JsonClass(generateAdapter = true)
data class GitHubBranch(
    val name: String = "",
    val protected: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GitHubCommitWrapper(
    val sha: String = "",
    val commit: GitHubCommitDetail = GitHubCommitDetail()
)

@JsonClass(generateAdapter = true)
data class GitHubCommitDetail(
    val message: String = "",
    val author: GitHubCommitAuthor = GitHubCommitAuthor()
)

@JsonClass(generateAdapter = true)
data class GitHubCommitAuthor(
    val name: String = "",
    val date: String = ""
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowRunsResponse(
    @Json(name = "workflow_runs") val workflowRuns: List<GitHubWorkflowRun> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowRun(
    val id: Long = 0L,
    val name: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    @Json(name = "head_branch") val headBranch: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "html_url") val htmlUrl: String = ""
)

@JsonClass(generateAdapter = true)
data class WorkflowDispatchRequest(
    val ref: String,
    val inputs: Map<String, String> = emptyMap()
)

interface GitHubApi {
    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") authHeader: String? = null
    ): GitHubRepoInfo

    @GET("repos/{owner}/{repo}/branches")
    suspend fun getBranches(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20,
        @Header("Authorization") authHeader: String? = null
    ): List<GitHubBranch>

    @GET("repos/{owner}/{repo}/commits")
    suspend fun getCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("sha") branch: String,
        @Query("per_page") perPage: Int = 10,
        @Header("Authorization") authHeader: String? = null
    ): List<GitHubCommitWrapper>

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 10,
        @Header("Authorization") authHeader: String? = null
    ): GitHubWorkflowRunsResponse

    @POST("repos/{owner}/{repo}/actions/workflows/{workflow}/dispatches")
    suspend fun triggerWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow") workflowFile: String,
        @Header("Authorization") authHeader: String,
        @Body body: WorkflowDispatchRequest
    ): Response<ResponseBody>
}

object VcsClient {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PKGForge-Android")
                .build()
            chain.proceed(req)
        }
        .build()

    val api: GitHubApi = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GitHubApi::class.java)

    fun generateCiWorkflowYaml(
        projectName: String,
        branch: String,
        deployEnv: String,
        titleId: String
    ): String = """
        name: Build & Deploy $projectName
        on:
          push:
            branches: [ "$branch" ]
          workflow_dispatch:
            inputs:
              environment:
                description: "Target deployment channel"
                required: true
                default: "$deployEnv"
        jobs:
          build-and-package:
            runs-on: ubuntu-latest
            steps:
              - uses: actions/checkout@v4
              - name: Validate PARAM.SFO & Title ID ($titleId)
                run: |
                  echo "Validating Title ID: $titleId"
                  sha256sum param.sfo || true
              - name: Build & Sign Artifact
                run: make package TITLE_ID=$titleId ENV=${'$'}{{ github.event.inputs.environment || '$deployEnv' }}
              - name: Upload Cloud Artifact
                uses: actions/upload-artifact@v4
                with:
                  name: ${projectName.lowercase().replace(" ", "-")}-pkg
                  path: dist/*.pkg
    """.trimIndent()
}
