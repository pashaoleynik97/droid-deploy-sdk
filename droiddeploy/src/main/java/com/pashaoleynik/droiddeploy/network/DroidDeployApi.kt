package com.pashaoleynik.droiddeploy.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

internal interface DroidDeployApi {

    @POST("api/v1/auth/apikey")
    suspend fun loginWithApiKey(
        @Body request: ApiKeyLoginRequest
    ): Response<RestResponse<ApiTokenDto>>

    @GET("api/v1/application/{applicationId}/version/latest")
    suspend fun getLatestVersion(
        @Path("applicationId") applicationId: String
    ): Response<RestResponse<VersionDto>>

    @GET("api/v1/application/{applicationId}/version/{versionCode}/apk")
    suspend fun downloadApk(
        @Path("applicationId") applicationId: String,
        @Path("versionCode") versionCode: Long
    ): Response<ResponseBody>
}
