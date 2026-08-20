package com.sunpanel.widget.api

import com.sunpanel.widget.data.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Sun-Panel API 接口定义
 * 所有接口均为 POST 请求
 * 认证方式：请求头 token（不是 Authorization: Bearer！）
 * 详见源码 service/api/api_v1/middleware/LoginInterceptor.go
 */
interface SunPanelApiService {

    // ========== 系统接口 ==========

    /** 登录：获取 Token */
    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginData>

    // ========== 面板内部接口（旧版，用 token 请求头）==========

    /** 获取所有分组
     * 注意：返回格式是 SuccessListData -> data: {list: [...], count: N}
     */
    @POST("/api/panel/itemIconGroup/getList")
    suspend fun getGroups(): ApiResponse<ItemIconGroupListData>

    /** 获取指定分组下的书签列表
     * 注意：返回格式是 SuccessListData -> data: {list: [...], count: N}
     */
    @POST("/api/panel/itemIcon/getListByGroupId")
    suspend fun getBookmarksByGroup(@Body request: GetListByGroupIdRequest): ApiResponse<ItemIconListData>

    // ========== OpenAPI V1 接口（新版 v1.4+）==========
    // 注意：OpenAPI 的 token 在管理后台"OpenAPI 应用"生成，与登录会话 token 不同。
    // 登录会话 token → 面板内部接口（读分组/书签）
    // API Token → OpenAPI 接口（写操作：创建/修改书签）

    /** 获取版本号（用于测试连通性，v1.7.*+） */
    @POST("/openapi/v1/version")
    suspend fun getVersion(): ApiResponse<VersionData>

    /** 获取所有分组（OpenAPI 方式） */
    @POST("/openapi/v1/itemGroup/getList")
    suspend fun getGroupsOpenApi(): ApiResponse<OpenApiGroupListData>

    /** 根据唯一标识查询单个书签（OpenAPI） */
    @POST("/openapi/v1/item/getInfoByOnlyName")
    suspend fun getBookmarkByOnlyName(@Body body: Map<String, String>): ApiResponse<OpenApiItemInfo>

    // ========== OpenAPI 写操作（后续功能预留） ==========

    /** 创建新分组 */
    @POST("/openapi/v1/itemGroup/create")
    suspend fun createGroup(@Body body: Map<String, String>): ApiResponse<Any>

    /** 创建新书签 */
    @POST("/openapi/v1/item/create")
    suspend fun createBookmark(@Body body: Map<String, Any?>): ApiResponse<Any>

    /** 修改书签（v1.7.*+，不需要修改的参数无需传） */
    @POST("/openapi/v1/item/update")
    suspend fun updateBookmark(@Body body: Map<String, Any?>): ApiResponse<Any>

    /** 批量修改书签（v2.0.0-dev-13+） */
    @POST("/openapi/v1/item/batchUpdate")
    suspend fun batchUpdateBookmarks(@Body body: Map<String, Any>): ApiResponse<BatchUpdateResponse>
}

/**
 * API 客户端工厂
 */
object SunPanelApi {

    private var retrofit: Retrofit? = null
    private var service: SunPanelApiService? = null
    private var lastToken: String? = null

    /**
     * 获取或创建 API Service 实例
     * @param baseUrl Sun-Panel 服务器地址，如 http://192.168.1.100:3002
     * @param token 认证 Token（登录返回的 token，或 OpenAPI 应用生成的 token）
     *
     * 注意：Sun-Panel 认证使用请求头 "token"（不是 "Authorization: Bearer"），
     * 详见 service/api/api_v1/middleware/LoginInterceptor.go
     */
    fun getService(baseUrl: String, token: String): SunPanelApiService {
        val normalizedUrl = normalizeUrl(baseUrl)

        // 如果 URL 或 Token 变化，重新创建
        if (service == null || retrofit?.baseUrl().toString() != normalizedUrl || lastToken != token) {
            lastToken = token

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    // 注意：Sun-Panel 源码 (middleware/LoginInterceptor.go)
                    // 读取的是名为 "token" 的请求头，不是 "Authorization: Bearer xxx"！
                    val request = original.newBuilder()
                        .header("token", token)
                        .header("Content-Type", "application/json")
                        .method(original.method, original.body)
                        .build()
                    chain.proceed(request)
                }
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            service = retrofit!!.create(SunPanelApiService::class.java)
        }
        return service!!
    }

    /**
     * 确保 URL 格式正确，以 / 结尾
     */
    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        if (!normalized.endsWith("/")) {
            normalized = "$normalized/"
        }
        return normalized
    }

    /**
     * 清除缓存的 Retrofit 实例（切换服务器时）
     */
    fun reset() {
        retrofit = null
        service = null
        lastToken = null
    }
}