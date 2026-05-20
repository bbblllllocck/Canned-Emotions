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
    var updatedAtMillis: Long = System.currentTimeMillis(),


    //new parameters
    var musicType: Int = -1, //0:纯音乐，1:单曲
    var durationMs: Long = -1,
    var lastPlayedDate: Long = 0,
    var integratedTimeParameter: Float = 0f, //这玩意我是这么想，毕竟总的播放时长/次数不能参与计算，所以就这样，每次播完之后这玩意加一个值，然后随时间衰减，衰减的值拿上次播放，然后实时更新。
) {
    companion object TaskStatus {
        const val PENDING = 0    // 刚扫出来，等 AI 处理
        const val DONE = 1       // AI 处理完毕，已有向量
        const val UNEXIST = 2    // 幽灵数据（文件已被用户从手机里删除）
    }
}
