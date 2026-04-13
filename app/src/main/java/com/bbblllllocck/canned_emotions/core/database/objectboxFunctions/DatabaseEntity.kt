package com.bbblllllocck.canned_emotions.core.database.objectboxFunctions

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.annotation.VectorDistanceType

@Suppress("ArrayInDataClass")
@Entity
data class MusicScanTaskEntity(
    @Id var id: Long = 0,
    @Unique var filePath: String = "",
    var title: String = "",
    var album: String = "",
    var artist: String = "",
    var status: Int = PENDING,

    @HnswIndex(dimensions = 3072, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray? = null,

    var createdAtMillis: Long = System.currentTimeMillis(),
    var updatedAtMillis: Long = System.currentTimeMillis()
) {
    companion object TaskStatus {
        const val PENDING = 0    // 刚扫出来，等 AI 处理
        const val DONE = 1       // AI 处理完毕，已有向量
        const val UNEXIST = 2    // 幽灵数据（文件已被用户从手机里删除）
    }
}
