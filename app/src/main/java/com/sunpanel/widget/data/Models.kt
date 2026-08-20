package com.sunpanel.widget.data

/**
 * Sun-Panel API 响应通用包装
 * 所有接口返回格式: { "code": 0, "data": ..., "msg": "success" }
 */
data class ApiResponse<T>(
    val code: Int = -1,
    val data: T? = null,
    val msg: String = ""
)

/**
 * 登录请求
 */
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * 登录响应数据
 */
data class LoginData(
    val token: String = "",
    val id: Int = 0,
    val name: String = "",
    val headImage: String = ""
)

/**
 * 分组（Group）数据
 * 对应 Sun-Panel 的 itemIconGroup / OpenAPI 的 itemGroup
 */
data class ItemIconGroup(
    val id: Int = 0,
    val title: String = "",
    val icon: String? = null,
    val sort: Int = 0,
    /** OpenAPI 唯一标识（onlyName），可选 */
    val onlyName: String? = null,
    val createTime: String? = null,
    val updateTime: String? = null
)

/**
 * 图标信息
 */
data class ItemIcon(
    val itemType: Int = 0,
    val src: String? = null,
    val text: String? = null,
    val backgroundColor: String? = null
)

/**
 * 书签（Bookmark）数据
 * 对应 Sun-Panel 的 itemIcon
 */
data class ItemIconInfo(
    val id: Int = 0,
    val title: String = "",
    val url: String = "",
    val icon: ItemIcon? = null,
    val sort: Int = 0,
    val lanUrl: String? = null,
    val description: String? = null,
    val openMethod: Int = 0,
    val itemIconGroupId: Int? = null,
    val createTime: String? = null,
    val updateTime: String? = null
)

/**
 * 获取书签列表的请求参数
 */
data class GetListByGroupIdRequest(
    val itemIconGroupId: Int
)

/**
 * 书签列表响应（内部接口返回格式：{list: [...], count: N}）
 * 对应 apiReturn.SuccessListData()
 */
data class ItemIconListData(
    val list: List<ItemIconInfo> = emptyList(),
    val count: Int = 0
)

/**
 * 分组列表数据包装（内部接口 SuccessListData 格式）
 * 返回: { "count": 0, "list": [...] }
 */
data class ItemIconGroupListData(
    val count: Int = 0,
    val list: List<ItemIconGroup> = emptyList()
)

/**
 * 批量修改书签响应（v2.0.0-dev-13+）
 */
data class BatchUpdateResponse(
    val successCount: Int = 0,
    val failCount: Int = 0,
    val failItems: List<BatchUpdateFailItem> = emptyList()
)

data class BatchUpdateFailItem(
    val onlyName: String = "",
    val error: String = ""
)

/**
 * 本地缓存的书签结构 — 按分组组织
 */
data class CachedGroupData(
    val group: ItemIconGroup,
    val bookmarks: List<ItemIconInfo>
)

/**
 * 完整的缓存数据
 */
data class CachedPanelData(
    val groups: List<CachedGroupData> = emptyList()
)


// ========== 桌面小部件显示数据模型 ==========

/**
 * 小部件列表中的显示项（分组标题 或 书签）
 */
sealed class WidgetDisplayItem {
    /** 分组标题行 */
    data class Header(val groupName: String) : WidgetDisplayItem()
    /** 书签行 */
    data class Bookmark(val item: ItemIconInfo) : WidgetDisplayItem()
}

/**
 * 将缓存数据转换为小部件展平列表
 */
fun CachedPanelData.toWidgetDisplayList(): List<WidgetDisplayItem> {
    val result = mutableListOf<WidgetDisplayItem>()
    for (groupData in groups) {
        if (groupData.group.title.isNotBlank()) {
            result.add(WidgetDisplayItem.Header(groupData.group.title))
        }
        for (bookmark in groupData.bookmarks) {
            result.add(WidgetDisplayItem.Bookmark(bookmark))
        }
    }
    return result
}


// ========== OpenAPI V1 数据模型（v1.4+ 新增）==========

/**
 * OpenAPI 版本信息
 * /openapi/v1/version
 */
data class VersionData(
    val version: String = "",
    val versionCode: Int = 0
)

/**
 * OpenAPI 分组列表数据
 * /openapi/v1/itemGroup/getList
 * 返回: { "count": 3, "list": [ { "itemGroupID": 1, "title": "Group 1", "onlyName": "group1" } ] }
 */
data class OpenApiGroupListData(
    val count: Int = 0,
    val list: List<OpenApiItemGroup> = emptyList()
)

/**
 * OpenAPI 分组
 */
data class OpenApiItemGroup(
    val itemGroupID: Int = 0,
    val title: String = "",
    val onlyName: String? = null
) {
    /** 转换为应用内部统一的分组结构 */
    fun toItemIconGroup(): ItemIconGroup = ItemIconGroup(
        id = itemGroupID,
        title = title,
        onlyName = onlyName
    )
}

/**
 * OpenAPI 书签列表数据
 */
data class OpenApiItemListData(
    val count: Int = 0,
    val list: List<OpenApiItemInfo> = emptyList()
)

/**
 * OpenAPI 书签信息
 * /openapi/v1/item/getInfoByOnlyName
 * 返回: { "iconUrl": "...", "title": "...", "onlyName": "...", "url": "...", "lanUrl": "...", "description": "...", "itemGroupID": 1 }
 */
data class OpenApiItemInfo(
    val onlyName: String? = null,
    val iconUrl: String? = null,
    val title: String = "",
    val url: String = "",
    val lanUrl: String? = null,
    val description: String? = null,
    val itemGroupID: Int = 0
) {
    /** 转换为应用内部统一的书签结构 */
    fun toItemIconInfo(): ItemIconInfo = ItemIconInfo(
        title = title,
        url = url,
        lanUrl = lanUrl,
        description = description,
        itemIconGroupId = itemGroupID.takeIf { it > 0 },
        icon = iconUrl?.let { src ->
            ItemIcon(itemType = 0, src = src)
        }
    )
}