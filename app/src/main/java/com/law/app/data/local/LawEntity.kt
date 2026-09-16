package com.law.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 法规本地缓存实体
 * - 用于离线缓存搜索结果
 * - 保存收藏状态与阅读历史
 */
@Entity(tableName = "laws")
data class LawEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val type: String = "",
    val typeName: String = "",
    val lawLevel: String = "",
    val issuingAuthority: String = "",
    val publishDate: String = "",
    val effectiveDate: String = "",
    val documentNumber: String = "",
    val summary: String = "",
    val content: String = "",
    val pdfUrl: String? = null,
    val wpsUrl: String? = null,
    val detailUrl: String? = null,
    /** OSS PDF 文件路径（新版 API） */
    val ossPdfPath: String? = null,
    /** OSS Word 文件路径（新版 API） */
    val ossWordPath: String? = null,
    /** 目录树 JSON（新版 API） */
    val contentTreeJson: String? = null,
    /** 效力状态 */
    val status: Int? = null,
    val isFavorite: Boolean = false,
    val lastReadTime: Long = 0L,
    val cachedAt: Long = System.currentTimeMillis()
)
