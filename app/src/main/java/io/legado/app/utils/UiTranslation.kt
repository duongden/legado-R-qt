package io.legado.app.utils

import android.content.res.Configuration
import android.content.res.Resources
import io.legado.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.util.Locale

/** Display-only translations. Prefer the app's Vietnamese labels over novel dictionaries. */
object UiTranslation {
    private val displayCache = android.util.LruCache<String, String>(512)
    fun cached(text: String): String? = displayCache.get("${TranslateUtils.revisionKey()}:$text")

    /** App language controls UI independently of the novel translation switch. */
    fun isEnabled(): Boolean =
        appCtx.getPrefString(io.legado.app.constant.PreferKey.language) == "vi" ||
            androidx.core.os.ConfigurationCompat.getLocales(appCtx.resources.configuration)[0]?.language == "vi" ||
            TranslateUtils.isTranslateEnabled()

    private val builtinLabels = auditedUiLabels + mapOf(
        "书源详情" to "Chi tiết nguồn",
        "目录预览" to "Xem trước mục lục",
        "图库预览" to "Xem trước thư viện ảnh",
        "摘录分享模板" to "Mẫu chia sẻ trích đoạn",
        "分享样式" to "Kiểu chia sẻ",
        "快速切换摘录分享图片的配色和字体，预览与分享图片会同步更新。" to "Đổi nhanh màu và phông chữ ảnh chia sẻ trích đoạn. Bản xem trước và ảnh chia sẻ được cập nhật đồng thời.",
        "管理正文长按摘录分享图片使用的 HTML 模板。预览只显示模板头部，分享时会生成完整图片。" to "Quản lý mẫu HTML dùng khi nhấn giữ để chia sẻ trích đoạn thành ảnh. Xem trước chỉ hiện phần đầu mẫu; khi chia sẻ sẽ tạo ảnh đầy đủ.",
        "经典" to "Cổ điển",
        "青绿" to "Xanh ngọc",
        "淡粉" to "Hồng nhạt",
        "咖啡" to "Nâu cà phê",
        "深蓝" to "Xanh đậm",
        "纸白" to "Trắng giấy",
        "配色" to "Màu sắc",
        "系统字体" to "Phông hệ thống",
        "衬线字体" to "Phông có chân",
        "圆体" to "Phông tròn",
        "等宽字体" to "Phông đơn cách",
        "大小倍率" to "Tỷ lệ kích thước",
        "日间常规色" to "Màu thường ban ngày",
        "日间强调色" to "Màu nhấn ban ngày",
        "夜间常规色" to "Màu thường ban đêm",
        "夜间强调色" to "Màu nhấn ban đêm",
        "SVG 模板" to "Mẫu SVG",
        "内置段评气泡" to "Bong bóng bình luận có sẵn",
        "已应用" to "Đang áp dụng",
        "内置" to "Có sẵn",
        "本地" to "Trên máy",
        "云端" to "Đám mây",
        "本地 + 云端" to "Trên máy + đám mây",
        "山茶花" to "Hoa trà",
        "山茶、枝叶与奶油色的花笺" to "Hoa trà, cành lá và giấy hoa màu kem",
        "A LETTER IN BLOOM" to "LÁ THƯ NỞ HOA",
        "把片刻留白，交给一场盛开。" to "Dành một khoảng lặng cho mùa hoa nở.",
        "极光航迹" to "Hành trình cực quang",
        "极光光带、群山与远行的坐标" to "Dải cực quang, núi non và tọa độ hành trình",
        "SOMEWHERE BEYOND" to "PHÍA BÊN KIA",
        "下一段旅程，从这一页出发。" to "Hành trình tiếp theo bắt đầu từ trang này."
    )
    fun builtinLabel(text: String): String = if (isEnabled()) builtinLabels[text] ?: text else text
    private val relativeTime = Regex("^(\\d+)(秒|分钟|小时|天|周|月|年)(前|后)$")
    private val timeUnits = mapOf("秒" to "giây", "分钟" to "phút", "小时" to "giờ",
        "天" to "ngày", "周" to "tuần", "月" to "tháng", "年" to "năm")
    fun vietnameseString(id: Int, vararg args: Any): String =
        resources(Locale.forLanguageTag("vi")).getString(id, *args)

    private fun resources(locale: Locale): Resources {
        val configuration = Configuration(appCtx.resources.configuration)
        configuration.setLocale(locale)
        return appCtx.createConfigurationContext(configuration).resources
    }

    // Initialized on IO by translate(), never while composing a settings row.
    private val labels by lazy {
        val vietnamese = resources(Locale.forLanguageTag("vi"))
        val originals = listOf(resources(Locale.CHINESE), resources(Locale.ENGLISH))
        buildMap<String, String> {
            // Common labels supplied by source loginUi scripts, without changing their action keys.
            putAll(builtinLabels)
            putAll(mapOf(
                "悬浮底栏" to "Thanh dưới nổi",
                "悬浮底栏 + 默认顶栏" to "Thanh dưới nổi + thanh trên mặc định",
                "无搜索悬浮底栏" to "Thanh dưới nổi ẩn tìm kiếm",
                "悬浮底栏(隐藏搜索) + 顶栏显示搜索" to "Thanh dưới nổi ẩn tìm kiếm + tìm kiếm trên thanh trên",
                "常规底栏" to "Thanh dưới thường",
                "常规底栏 + 常规顶栏" to "Thanh dưới thường + thanh trên thường",
                "侧边栏" to "Thanh bên",
                "侧边栏 + 默认顶栏" to "Thanh bên + thanh trên mặc định",
                "未启用" to "Chưa bật",
                "点击签到" to "Nhấn để điểm danh",
                "已购书籍" to "Sách đã mua",
                "今日限免" to "Miễn phí hôm nay",
                "帮助文档" to "Tài liệu trợ giúp",
                "新人必读" to "Dành cho người mới",
                "书源相关" to "Về nguồn sách",
                "请用已登入新版晋江的手机扫码【截图后去晋江扫码】" to "Dùng ứng dụng Tấn Giang bản mới đã đăng nhập để quét mã. Có thể chụp màn hình rồi mở Tấn Giang để quét ảnh.",
                "请在当前登入了晋江账号的手机上进入晋江小说阅读App-我的，点击右上角“扫码”图标，扫描下方二维码" to "Trên điện thoại đã đăng nhập Tấn Giang, mở ứng dụng đọc truyện → Tôi, nhấn biểu tượng quét mã ở góc trên bên phải rồi quét mã QR bên dưới.",
                "请扫码授权后，再点右上角的“√”，不要提前点，否则要重新扫码" to "Quét mã và cấp quyền xong mới nhấn dấu ✓ ở góc trên bên phải. Nhấn trước sẽ phải quét lại mã.",
                "请扫码授权后，再点右上角的“✓”，不要提前点，否则要重新扫码" to "Quét mã và cấp quyền xong mới nhấn dấu ✓ ở góc trên bên phải. Nhấn trước sẽ phải quét lại mã.",
                "登录出错" to "Lỗi đăng nhập",
                "未找到定时任务" to "Không tìm thấy tác vụ hẹn giờ",
                "未找到书源" to "Không tìm thấy nguồn sách",
                "已在登录界面" to "Đang ở màn hình đăng nhập",
                "源未配置登录" to "Nguồn chưa cấu hình đăng nhập",
                "扫码二维码" to "Quét mã QR",
                "全部" to "Tất cả",
                "搜索设置" to "Cài đặt tìm kiếm",
                "获取书架" to "Tải giá sách",
                "添加书架" to "Thêm giá sách",
                "删除书架" to "Xóa giá sách",
                "书架选择" to "Chọn giá sách",
                "分类选择" to "Chọn thể loại",
                "标签选择" to "Chọn nhãn",
                "扫码登录" to "Đăng nhập bằng mã QR",
                "账号" to "Tài khoản",
                "验证码" to "Mã xác minh",
                "使用说明" to "Hướng dẫn sử dụng",
                "网站充值" to "Nạp tiền trên website",
                "搜索" to "Tìm kiếm",
                "自动" to "Tự động",
                "替换" to "Thay thế",
                "夜间" to "Ban đêm",
                "目录" to "Mục lục",
                "朗读" to "Đọc to",
                "界面" to "Giao diện",
                "设置" to "Cài đặt",
                "连载中" to "Đang ra",
                "已完结" to "Đã hoàn thành",
                "完结" to "Hoàn thành",
                "登录书源" to "Đăng nhập nguồn",
                "注册书源" to "Đăng ký nguồn",
                "退出登录" to "Đăng xuất",
                "用户后台" to "Trang tài khoản",
                "书源设置中心" to "Cài đặt nguồn",
                "检测登录" to "Kiểm tra đăng nhập",
                "打赏享福利" to "Ủng hộ để nhận ưu đãi",
                "更新书源" to "Cập nhật nguồn",
                "番茄登录" to "Đăng nhập Fanqie",
                "清空设置" to "Xóa cài đặt",
                "设置检测" to "Kiểm tra cài đặt",
                "永久发布页" to "Trang phát hành chính thức",
                "清除设备" to "Xóa thiết bị",
                "切换服务器" to "Đổi máy chủ",
                "检测当前服务器" to "Kiểm tra máy chủ hiện tại",
                "使用教程" to "Hướng dẫn sử dụng",
                "发现页兼容" to "Tương thích trang khám phá"
            ))
            R.string::class.java.fields.forEach { field ->
                val id = field.getInt(null)
                val target = vietnamese.getString(id)
                if (target.none { it in '\u3400'..'\u9fff' }) {
                    originals.forEach { source ->
                        val raw = source.getString(id)
                        if (raw != target) putIfAbsent(raw, target)
                    }
                }
            }
            R.array::class.java.fields.forEach { field ->
                val id = field.getInt(null)
                // R.array also contains integer arrays; only aligned string arrays are labels.
                val target = runCatching { vietnamese.getStringArray(id) }.getOrNull()
                    ?: return@forEach
                originals.forEach { source ->
                    val raw = runCatching { source.getStringArray(id) }.getOrNull()
                    if (raw != null && raw.size == target.size) {
                        for (index in raw.indices) {
                            val original = raw[index] ?: continue
                            val translated = target[index] ?: continue
                            if (original != translated &&
                                translated.none { it in '\u3400'..'\u9fff' }) {
                                putIfAbsent(original, translated)
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun translateMessage(text: String): String =
        text.split('\n').map { line ->
            line.split(": ", limit = 2).map { translate(it) }.joinToString(": ")
        }.joinToString("\n")

    suspend fun translate(text: String): String {
        if (text.isBlank()) return text
        relativeTime.matchEntire(text)?.let { match ->
            val (count, unit, direction) = match.destructured
            return "$count ${timeUnits.getValue(unit)} ${if (direction == "前") "trước" else "sau"}"
        }
        val label = withContext(Dispatchers.IO) {
            labels[text] ?: labels[text.trim()]?.let { target ->
                text.takeWhile { it.isWhitespace() } + target + text.takeLastWhile { it.isWhitespace() }
            } ?: run {
                // Preserve emoji/decorations around a source-provided label.
                val start = text.indexOfFirst { it.isLetterOrDigit() }
                val end = text.indexOfLast { it.isLetterOrDigit() }
                if (start < 0) null else labels[text.substring(start, end + 1)]?.let {
                    text.substring(0, start) + it + text.substring(end + 1)
                }
            }
        }
        return UiTextSpacing.normalize(io.legado.app.model.TranslationEngine.normalizeDisplayText(label ?: TranslateUtils.translate(text))).also { displayCache.put("${TranslateUtils.revisionKey()}:$text", it) }
    }
}
