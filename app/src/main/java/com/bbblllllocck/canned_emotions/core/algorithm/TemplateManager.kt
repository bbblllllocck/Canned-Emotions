package com.bbblllllocck.canned_emotions.core.algorithm

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bbblllllocck.canned_emotions.core.api.AppContextProvider
import com.bbblllllocck.canned_emotions.core.database.objectboxFunctions.DatabaseManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

data class Template(
    val id: String,

    var name: String,

    //定义极大影响相关性系数，认为被该参数完整影响时会使结果和起始点完全不相关。
    var COEFFICIENT_OF_UNRELATED : Float = 0.17f,
    //归根结底，对于相似度的定义是关键的。也许是30%？


    //时间&次数综合推荐参数
    var enableIntegratedParameter: Boolean = true,
    var savedIntegratedParameterWeight: Float = 0.81021583f,
    var integratedParameterWeight: Float = 0.81021583f,//（0到1，1表示听1次后相关权重就COEFFICIENT_OF_UNRELATED，0.5就是听2次）
    var integratedParameterHalfLife: Int = 46,//（天）
    //UI上表示为：一首歌被推荐x次后就不大可能被推荐，x天后衰减一半。

    //专辑&艺术家查重系数
    var enableArtistDuplicate: Boolean = true,
    var savedArtistDuplicateCoefficient: Float = 4.1666667E-6f,
    var artistDuplicateCoefficient: Float = 4.1666667E-6f,//（0到1，0就是关，1就是一个艺术家听一ms后权重也直接减一个不相关系数，0.5两次，此项不包含赦免时间）
    var artistDuplicateFadeTime: Int = 1505439,//（ms，和上面那个参数配合使用的，查重系数均匀递减，或者别的什么，到时候看我用什么数学公式吧。那么它和（赦免时间与赦免时间加1/artistDuplicateCoefficientPerMinute）的平均值的比值就可以表示为——听了几个艺术家后你会想听原来的？不过太不直观了，还是按时间计吧）
    var artistPardonTime: Int = 180000,//（ms，艺术家赦免时间，这个时间内的听歌记录不计入查重惩罚的计算）
    //那么在UI上我希望是：在xx时间（赦免）到xx时间（查重）内被允许播放同一个艺术家的歌，xx时间（消逝）后完全清除，然后这仨最好在一个窗口里面设置，为一组。
    //赦免还是不能调大，不然容易打乒乓球。那这个，那还是不要这么描述吧，赦免时间应该是一个额外的东西。

    //和上面一样
    var enableAlbumDuplicate: Boolean = true,
    var savedAlbumDuplicateCoefficient: Float = 2.5000002E-6f,
    var albumDuplicateCoefficient: Float = 2.5000002E-6f,//（0到1，0就是关，1就是一个专辑听完一ms后权重也直接减一个不相关系数，0.5两次）
    var albumDuplicateFadeTime: Int = 739470,//（一样）
    var albumPardonTime: Int = 90000,//（ms，专辑赦免时间，这个时间内的听歌记录不计入查重惩罚的计算）


    //温度
    var temperature: Float = 0.01f,// 温度，不过我不太知道它具体怎么工作，反之得给playlist来一点随机性

    //惩罚向量
    var enablePunishment: Boolean = true,
    var savedPunishmentVectorWeight: Float = 0.15f,
    var punishmentVectorFadeTime: Int = 300000,//（ms，惩罚向量在此时间内平滑衰减）
    var punishmentVectorWeight: Float = 0.15f,//（0-1，惩罚向量的权重，为1时会极大影响结果，-30%，0为关）
    var punishmentVectorWeightWhenSwitch: Float = 0.3f,//（0-1，直接在播放列表里面换别的歌时惩罚向量的权重，当然如果是同一首可不能触发这个）
    //在UI上表现为，如果手动换歌会触发

    //单曲比例
    var songProportion: Float = 0.53847045f,//（0-1，0就是全是纯音乐1就是全是单曲。-1解就是不管。这里的“比例”定义是，比如说0.3，那单曲就是0.3，纯音乐0.7）
    var tolerance: Int = 300000,//（ms，播放时间*比例到达这个值会极大影响权重。）



    //扩散
    //我终于加上新的参数了，UI别忘了哦，虽然到时候具体的表现形式还有待讨论
    var roamingType: Int = 0,//（0-1，0为中心扩散，1为线性扩散，详见draft.md）

    /*回归长度，在线性扩散模式下会在此长度内均匀考虑与起始点的相似性，权重缓慢衰减*/
    var regressionLength: Int = 1800000,//（ms）
    var initialRegressionWeight: Float = 0.5f,//（0-1，线性扩散模式下，回归向量在回归长度内的初始权重，之后均匀衰减到0）

    var directionRatio: Float = 0.5f//（0-1，表示线性扩散的方向占中心回归向量之外的部分的多少比例，如：在第900000ms，中心回归向量削减到了40%，如果directionRatio是0.5，那么线性扩散的方向就占剩下的60%的50%，也就是30%，剩下的30%是上一首歌的向量。把这三个向量按比例加在一起，就得到了线性扩散模式的“特殊向量”


    )

private const val TEMPLATE_STORE_NAME = "template_store"
private const val TEMPLATE_LIST_KEY = "templates_json"
private const val USING_TEMPLATE_KEY = "using_template_id"
private const val SHOW_WEIGHT_DETAILS_KEY = "show_weight_details"
private const val SHOW_UNCERTAINTY_AREA_KEY = "show_uncertainty_area"

private val Context.templateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = TEMPLATE_STORE_NAME
)

object TemplateManager {
    // TODO: 以后可能需要单独写一个设置页面来专门管理这些配置项和开关。
    private val appContext: Context by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContextProvider.get() }
    private val dataStore: DataStore<Preferences> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        appContext.templateDataStore
    }
    private val templateListKey = stringPreferencesKey(TEMPLATE_LIST_KEY)
    private val usingTemplateKey = stringPreferencesKey(USING_TEMPLATE_KEY)
    private val showWeightDetailsKey = booleanPreferencesKey(SHOW_WEIGHT_DETAILS_KEY)
    private val showUncertaintyAreaKey = booleanPreferencesKey(SHOW_UNCERTAINTY_AREA_KEY)

    private val templatesState = MutableStateFlow(readAllOrNull().orEmpty())
    private val usingTemplateIdState = MutableStateFlow(readUsingIdOrNull())
    private val showWeightDetailsState = MutableStateFlow(readShowWeightDetailsOrNull() ?: false)
    private val showUncertaintyAreaState = MutableStateFlow(readShowUncertaintyAreaOrNull() ?: true)

    init {
        ensureDefaults()
    }

    fun observeAll(): Flow<List<Template>> = templatesState.asStateFlow()

    fun observeUsingTemplateId(): Flow<String?> = usingTemplateIdState.asStateFlow()

    fun observeWeightDisplayEnabled(): Flow<Boolean> = showWeightDetailsState.asStateFlow()
    
    fun observeShowUncertaintyAreaEnabled(): Flow<Boolean> = showUncertaintyAreaState.asStateFlow()

    fun setWeightDisplayEnabled(enabled: Boolean) {
        showWeightDetailsState.value = enabled
        runBlocking {
            dataStore.edit { prefs ->
                prefs[showWeightDetailsKey] = enabled
            }
        }
    }
    
    fun setShowUncertaintyAreaEnabled(enabled: Boolean) {
        showUncertaintyAreaState.value = enabled
        runBlocking {
            dataStore.edit { prefs ->
                prefs[showUncertaintyAreaKey] = enabled
            }
        }
    }


    fun addTemplate(template: Template) {
        val current = templatesState.value.toMutableList()
        val index = current.indexOfFirst { it.id == template.id }
        if (index >= 0) {
            current[index] = template
        } else {
            current.add(template)
        }
        val next = current.toList()
        saveAll(next)
        templatesState.value = next
        if (usingTemplateIdState.value == null) {
            setUsingTemplate(template.id)
        }
    }

    fun removeTemplate(templateId: String) {
        val current = templatesState.value
        if (current.none { it.id == templateId }) return

        val next = current.filterNot { it.id == templateId }
        val normalized = if (next.isEmpty()) listOf(Template(id = "default", name = "bbblllllocck乱调")) else next

        templatesState.value = normalized
        saveAll(normalized)

        val usingId = usingTemplateIdState.value
        if (usingId == null || usingId == templateId || normalized.none { it.id == usingId }) {
            setUsingTemplate(normalized.first().id)
        }
    }

    fun getUsingTemplate(): Template? {
        val id = usingTemplateIdState.value
        return templatesState.value.firstOrNull { it.id == id }
            ?: templatesState.value.firstOrNull()
    }

    fun setUsingTemplate(templateId: String) {
        if (templatesState.value.none { it.id == templateId }) return

        val previousId = usingTemplateIdState.value
        val previousTemplate = templatesState.value.firstOrNull { it.id == previousId }
        val nextTemplate = templatesState.value.firstOrNull { it.id == templateId }

        usingTemplateIdState.value = templateId
        runBlocking {
            dataStore.edit { prefs ->
                prefs[usingTemplateKey] = templateId
            }
        }

        if (previousTemplate != null && nextTemplate != null) {
            val oldWeight = previousTemplate.integratedParameterWeight
            val newWeight = nextTemplate.integratedParameterWeight
            if (oldWeight > 0f && newWeight > 0f && oldWeight != newWeight) {
                val ratio = newWeight / oldWeight
                DatabaseManager.scaleIntegratedParameters(ratio)
            }
        }
    }

    private fun ensureDefaults() {
        if (templatesState.value.isEmpty()) {
            val defaultTemplate = Template(id = "default", name = "bbblllllocck乱调")
            templatesState.value = listOf(defaultTemplate)
            saveAll(templatesState.value)
        }
        val usingId = usingTemplateIdState.value
        if (usingId == null || templatesState.value.none { it.id == usingId }) {
            val fallbackId = templatesState.value.first().id
            setUsingTemplate(fallbackId)
        }
    }

    private fun readAllOrNull(): List<Template>? {
        return runBlocking {
            val raw = dataStore.data.first()[templateListKey] ?: return@runBlocking emptyList()
            runCatching { decode(raw) }.getOrNull()
        }
    }

    private fun readUsingIdOrNull(): String? {
        return runBlocking { dataStore.data.first()[usingTemplateKey] }
    }

    private fun readShowWeightDetailsOrNull(): Boolean? {
        return runBlocking { dataStore.data.first()[showWeightDetailsKey] }
    }
    
    private fun readShowUncertaintyAreaOrNull(): Boolean? {
        return runBlocking { dataStore.data.first()[showUncertaintyAreaKey] }
    }

    private fun saveAll(items: List<Template>) {
        val encoded = runCatching { encode(items) }.getOrNull() ?: return
        runBlocking {
            dataStore.edit { prefs ->
                prefs[templateListKey] = encoded
            }
        }
    }





    private fun encode(items: List<Template>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("coefficientOfUnrelated", item.COEFFICIENT_OF_UNRELATED)
                    put("enableIntegratedParameter", item.enableIntegratedParameter)
                    put("savedIntegratedParameterWeight", item.savedIntegratedParameterWeight)
                    put("integratedParameterWeight", item.integratedParameterWeight)
                    put("integratedParameterHalfLife", item.integratedParameterHalfLife)
                    put("enableArtistDuplicate", item.enableArtistDuplicate)
                    put("savedArtistDuplicateCoefficient", item.savedArtistDuplicateCoefficient)
                    put("artistDuplicateCoefficient", item.artistDuplicateCoefficient)
                    put("artistDuplicateFadeTime", item.artistDuplicateFadeTime)
                    put("artistPardonTime", item.artistPardonTime)
                    put("enableAlbumDuplicate", item.enableAlbumDuplicate)
                    put("savedAlbumDuplicateCoefficient", item.savedAlbumDuplicateCoefficient)
                    put("albumDuplicateCoefficient", item.albumDuplicateCoefficient)
                    put("albumDuplicateFadeTime", item.albumDuplicateFadeTime)
                    put("albumPardonTime", item.albumPardonTime)
                    put("temperature", item.temperature)
                    put("enablePunishment", item.enablePunishment)
                    put("savedPunishmentVectorWeight", item.savedPunishmentVectorWeight)
                    put("punishmentVectorFadeTime", item.punishmentVectorFadeTime)
                    put("punishmentVectorWeight", item.punishmentVectorWeight)
                    put("punishmentVectorWeightWhenSwitch", item.punishmentVectorWeightWhenSwitch)
                    put("songProportion", item.songProportion)
                    put("tolerance", item.tolerance)
                    put("roamingType", item.roamingType)
                    put("regressionLength", item.regressionLength)
                    put("initialRegressionWeight", item.initialRegressionWeight)
                    put("directionRatio", item.directionRatio)
                }
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<Template> {
        val array = JSONArray(raw)
        return List(array.length()) { index ->
            val obj = array.getJSONObject(index)
            val id = obj.optString("id")
            val name = obj.optString("name")
            if (id.isBlank() || name.isBlank()) return@List null

            val base = Template(id = id, name = name)
            base.copy(
                COEFFICIENT_OF_UNRELATED = obj.optDouble(
                    "coefficientOfUnrelated",
                    base.COEFFICIENT_OF_UNRELATED.toDouble()
                ).toFloat(),
                enableIntegratedParameter = obj.optBoolean("enableIntegratedParameter", base.enableIntegratedParameter),
                savedIntegratedParameterWeight = obj.optDouble(
                    "savedIntegratedParameterWeight",
                    base.savedIntegratedParameterWeight.toDouble()
                ).toFloat(),
                integratedParameterWeight = obj.optDouble(
                    "integratedParameterWeight",
                    base.integratedParameterWeight.toDouble()
                ).toFloat(),
                integratedParameterHalfLife = obj.optInt(
                    "integratedParameterHalfLife",
                    base.integratedParameterHalfLife
                ),
                enableArtistDuplicate = obj.optBoolean("enableArtistDuplicate", base.enableArtistDuplicate),
                savedArtistDuplicateCoefficient = obj.optDouble(
                    "savedArtistDuplicateCoefficient",
                    base.savedArtistDuplicateCoefficient.toDouble()
                ).toFloat(),
                artistDuplicateCoefficient = obj.optDouble(
                    "artistDuplicateCoefficient",
                    base.artistDuplicateCoefficient.toDouble()
                ).toFloat(),
                artistDuplicateFadeTime = obj.optInt(
                    "artistDuplicateFadeTime",
                    base.artistDuplicateFadeTime
                ),
                artistPardonTime = obj.optInt("artistPardonTime", base.artistPardonTime),
                enableAlbumDuplicate = obj.optBoolean("enableAlbumDuplicate", base.enableAlbumDuplicate),
                savedAlbumDuplicateCoefficient = obj.optDouble(
                    "savedAlbumDuplicateCoefficient",
                    base.savedAlbumDuplicateCoefficient.toDouble()
                ).toFloat(),
                albumDuplicateCoefficient = obj.optDouble(
                    "albumDuplicateCoefficient",
                    base.albumDuplicateCoefficient.toDouble()
                ).toFloat(),
                albumDuplicateFadeTime = obj.optInt("albumDuplicateFadeTime", base.albumDuplicateFadeTime),
                albumPardonTime = obj.optInt("albumPardonTime", base.albumPardonTime),
                temperature = obj.optDouble("temperature", base.temperature.toDouble()).toFloat(),
                enablePunishment = obj.optBoolean("enablePunishment", base.enablePunishment),
                savedPunishmentVectorWeight = obj.optDouble(
                    "savedPunishmentVectorWeight",
                    base.savedPunishmentVectorWeight.toDouble()
                ).toFloat(),
                punishmentVectorFadeTime = obj.optInt(
                    "punishmentVectorFadeTime",
                    base.punishmentVectorFadeTime
                ),
                punishmentVectorWeight = obj.optDouble(
                    "punishmentVectorWeight",
                    base.punishmentVectorWeight.toDouble()
                ).toFloat(),
                punishmentVectorWeightWhenSwitch = obj.optDouble(
                    "punishmentVectorWeightWhenSwitch",
                    base.punishmentVectorWeightWhenSwitch.toDouble()
                ).toFloat(),
                songProportion = obj.optDouble("songProportion", base.songProportion.toDouble()).toFloat(),
                tolerance = obj.optInt("tolerance", base.tolerance),
                roamingType = obj.optInt("roamingType", base.roamingType),
                regressionLength = obj.optInt("regressionLength", base.regressionLength),
                initialRegressionWeight = obj.optDouble(
                    "initialRegressionWeight",
                    base.initialRegressionWeight.toDouble()
                ).toFloat(),
                directionRatio = obj.optDouble("directionRatio", base.directionRatio.toDouble()).toFloat()
            )
        }.filterNotNull()
    }

}