package com.minimalist.finance.util

import android.content.Context
import android.net.Uri
import com.minimalist.finance.data.AppDatabase
import com.minimalist.finance.data.model.Account
import com.minimalist.finance.data.model.Record
import com.minimalist.finance.data.model.TransactionType
import com.minimalist.finance.service.AutoRecordAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

/**
 * 官方账单智能导入引擎 (Universal Bill Import Engine)
 *
 * 核心特性：
 * 1. 字符集多级智能嗅探：完美兼容支付宝的 GBK/CP936 编码与微信的 UTF-8/UTF-8 BOM，彻底根除乱码；
 * 2. 格式与表头自动嗅探：支持微信支付、支付宝、云闪付与数簿原生备份四种格式，自动跳过前置元数据说明行；
 * 3. 绝对幂等去重：基于官方 28-32 位全局唯一交易单号（Order ID）进行数据库批量查重，同一个账单重复导入 100 次也零重复；
 * 4. 智能过滤与分类映射：自动剔除“不计收支”、“退款”等中性流水，无缝映射数簿分类系统。
 */
object BillImportEngine {

    enum class BillFormat(val displayName: String) {
        WECHAT("微信支付账单"),
        ALIPAY("支付宝账单"),
        UNIONPAY("云闪付账单"),
        SHUBU("数簿原生备份"),
        UNKNOWN("未知格式")
    }

    data class ParsedBill(
        val timestamp: Long,
        val type: TransactionType,
        val amount: Double,
        val categoryName: String,
        val accountName: String,
        val merchant: String,
        val remark: String,
        val orderId: String? = null,
        val tag: String = ""
    )

    data class ImportResult(
        val success: Boolean,
        val format: BillFormat = BillFormat.UNKNOWN,
        val totalCount: Int = 0,
        val importedCount: Int = 0,
        val duplicateCount: Int = 0,
        val filteredCount: Int = 0,
        val failedCount: Int = 0,
        val message: String = "",
        val errors: List<String> = emptyList()
    )

    // ==================== 1. 字符集自动探测 ====================

    private val GBK_CHARSET: Charset by lazy {
        try {
            Charset.forName("GBK")
        } catch (_: Exception) {
            Charset.forName("GB2312")
        }
    }

    /**
     * 自动探测字节数组的编码格式（UTF-8, UTF-8-BOM, GBK）
     */
    fun detectCharset(bytes: ByteArray): Charset {
        // 1. 检测 UTF-8 BOM (EF BB BF)
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return StandardCharsets.UTF_8
        }
        // 2. 检测 UTF-16 LE/BE BOM
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return StandardCharsets.UTF_16LE
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return StandardCharsets.UTF_16BE
        }

        // 3. 严格尝试 UTF-8 解码
        val utf8Decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        return try {
            utf8Decoder.decode(ByteBuffer.wrap(bytes))
            StandardCharsets.UTF_8
        } catch (_: Exception) {
            // UTF-8 出现非法序列，绝大多数为支付宝导出的 GBK / CP936 中文字符集
            GBK_CHARSET
        }
    }

    /**
     * 安全将输入流解码为无 BOM 的纯文本字符串
     */
    fun decodeStreamToString(inputStream: InputStream): String {
        val bytes = inputStream.readBytes()
        val charset = detectCharset(bytes)
        val offset = if (charset == StandardCharsets.UTF_8 && bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) 3 else 0
        return String(bytes, offset, bytes.size - offset, charset)
    }

    // ==================== 2. 格式嗅探与 CSV 解析 ====================

    data class HeaderSniffResult(
        val format: BillFormat,
        val headerIndex: Int,
        val headers: List<String>
    )

    /**
     * 解析带引号、转义与逗号的单行 CSV
     */
    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    /**
     * 自动在全文中搜寻真实的表头行，并判定账单来源
     */
    fun sniffFormatAndHeader(lines: List<String>): HeaderSniffResult? {
        for ((index, rawLine) in lines.withIndex()) {
            val line = rawLine.trim().trim('\uFEFF')
            if (line.isEmpty() || line.startsWith("-")) continue

            val cols = parseCsvLine(line)
            if (cols.size < 4) continue

            // 1. 数簿自身导出的 CSV: "时间,账本,类型,金额,分类,账户,备注,标签"
            if (cols.contains("时间") && cols.contains("类型") && cols.contains("金额") && (cols.contains("分类") || cols.contains("账户"))) {
                return HeaderSniffResult(BillFormat.SHUBU, index, cols)
            }

            // 2. 微信支付账单表头
            // "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注"
            if (cols.contains("交易时间") && cols.contains("收/支") && (cols.contains("金额(元)") || cols.contains("金额")) && cols.contains("交易单号")) {
                return HeaderSniffResult(BillFormat.WECHAT, index, cols)
            }

            // 3. 支付宝账单表头 (新版与旧版)
            // 新版："交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注"
            // 旧版："交易号,商户订单号,交易创建时间,付款时间,最近修改时间,交易来源地,类型,交易对方,商品名称,金额（元）,收/支,交易状态..."
            if (cols.contains("收/支") && (cols.contains("交易订单号") || cols.contains("交易号")) && (cols.contains("金额") || cols.contains("金额（元）"))) {
                return HeaderSniffResult(BillFormat.ALIPAY, index, cols)
            }

            // 4. 云闪付账单表头
            // "交易时间,流水号/参考号,交易类型,交易金额,交易状态..."
            if ((cols.contains("流水号") || cols.contains("参考号") || cols.contains("交易流水号")) &&
                (cols.contains("交易金额") || cols.contains("交易金额(元)") || cols.contains("金额"))
            ) {
                return HeaderSniffResult(BillFormat.UNIONPAY, index, cols)
            }
        }
        return null
    }

    // ==================== 3. 字段提取与清洗工具 ====================

    private val supportedDateFormats = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm",
        "yyyyMMddHHmmss",
        "yyyy-MM-dd",
        "yyyy/MM/dd"
    )

    private fun parseTimestamp(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val clean = text.trim()
        for (format in supportedDateFormats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.CHINA)
                val date = sdf.parse(clean)
                if (date != null) return date.time
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseAmount(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        val clean = text.trim()
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .replace("元", "")
            .replace(" ", "")
            .replace("\u00A0", "")
        if (clean.isEmpty() || clean == "/" || clean == "-") return null
        return clean.toDoubleOrNull()?.let { abs(it) }
    }

    // ==================== 4. 各平台解析器实现 ====================

    private fun parseWeChatRow(row: Map<String, String>): ParsedBill? {
        val timeStr = row["交易时间"]
        val timestamp = parseTimestamp(timeStr) ?: return null

        val status = row["当前状态"]?.trim() ?: ""
        // 过滤退款、关闭、失败等非完成交易
        if (status.contains("退款") || status.contains("已退款") || status.contains("对方已退还") ||
            status.contains("关闭") || status.contains("失败")
        ) {
            return null
        }

        // 判定收支类型
        val typeStr = row["收/支"]?.trim() ?: ""
        val type = when {
            typeStr.contains("支出") -> TransactionType.EXPENSE
            typeStr.contains("收入") -> TransactionType.INCOME
            else -> return null // "/" 代表不计收支中性交易（零钱充值、信用卡还款、提现等），直接过滤
        }

        val amount = parseAmount(row["金额(元)"] ?: row["金额"]) ?: return null
        if (amount <= 0.0) return null

        val party = row["交易对方"]?.trim()?.takeIf { it != "/" } ?: ""
        val product = row["商品"]?.trim()?.takeIf { it != "/" } ?: ""
        val originalRemark = row["备注"]?.trim()?.takeIf { it != "/" } ?: ""
        val tradeType = row["交易类型"]?.trim() ?: ""

        val merchantName = party.ifBlank { product.ifBlank { "微信消费" } }
        val remarkText = if (product.isNotBlank() && product != merchantName) product else originalRemark

        // 提取支付方式/账户
        val payMethod = row["支付方式"]?.trim() ?: "微信"
        val accountName = when {
            payMethod.contains("零钱通") -> "零钱通"
            payMethod.contains("零钱") -> "微信零钱"
            payMethod.contains("信用卡") || payMethod.contains("储蓄卡") || payMethod.contains("借记卡") || payMethod.contains("银行") -> {
                val bankNames = listOf(
                    "招商银行", "工商银行", "建设银行", "农业银行", "中国银行", "交通银行",
                    "平安银行", "中信银行", "浦发银行", "民生银行", "光大银行", "广发银行",
                    "兴业银行", "华夏银行", "北京银行", "上海银行", "邮储银行", "微众银行"
                )
                bankNames.firstOrNull { payMethod.contains(it) } ?: payMethod
            }
            else -> "微信"
        }

        val categoryName = AutoRecordAccessibilityService.classifyCategory("$merchantName $remarkText $tradeType", type)
        val orderId = row["交易单号"]?.trim()?.takeIf { it.isNotBlank() && it != "/" }

        return ParsedBill(
            timestamp = timestamp,
            type = type,
            amount = amount,
            categoryName = categoryName,
            accountName = accountName,
            merchant = merchantName,
            remark = remarkText,
            orderId = orderId
        )
    }

    private fun parseAlipayRow(row: Map<String, String>): ParsedBill? {
        val timeStr = row["交易时间"] ?: row["付款时间"] ?: row["交易创建时间"]
        val timestamp = parseTimestamp(timeStr) ?: return null

        val status = row["交易状态"]?.trim() ?: ""
        // 过滤退款成功、交易关闭、等待付款等
        if (status.contains("退款") || status.contains("关闭") || status.contains("失败") || status.contains("等待付款")) {
            return null
        }

        val typeStr = row["收/支"]?.trim() ?: ""
        val type = when {
            typeStr.contains("支出") -> TransactionType.EXPENSE
            typeStr.contains("收入") -> TransactionType.INCOME
            else -> return null // "不计收支" 或 "/" 过滤
        }

        val amount = parseAmount(row["金额"] ?: row["金额（元）"]) ?: return null
        if (amount <= 0.0) return null

        val party = row["交易对方"]?.trim()?.takeIf { it != "/" } ?: ""
        val product = row["商品说明"]?.trim()?.takeIf { it != "/" } ?: row["商品名称"]?.trim()?.takeIf { it != "/" } ?: ""
        val originalRemark = row["备注"]?.trim()?.takeIf { it != "/" } ?: ""
        val alipayCategory = row["交易分类"]?.trim() ?: ""

        val merchantName = party.ifBlank { product.ifBlank { "支付宝消费" } }
        val remarkText = if (product.isNotBlank() && product != merchantName) product else originalRemark

        // 提取收/付款方式
        val payMethod = row["收/付款方式"]?.trim() ?: "支付宝"
        val accountName = when {
            payMethod.contains("花呗") -> "花呗"
            payMethod.contains("余额宝") -> "余额宝"
            payMethod.contains("余额") -> "支付宝余额"
            payMethod.contains("信用卡") || payMethod.contains("储蓄卡") || payMethod.contains("借记卡") || payMethod.contains("银行") -> {
                val bankNames = listOf(
                    "招商银行", "工商银行", "建设银行", "农业银行", "中国银行", "交通银行",
                    "平安银行", "中信银行", "浦发银行", "民生银行", "光大银行", "广发银行",
                    "兴业银行", "华夏银行", "北京银行", "上海银行", "邮储银行"
                )
                bankNames.firstOrNull { payMethod.contains(it) } ?: payMethod
            }
            else -> "支付宝"
        }

        // 分类映射：优先对齐支付宝官方分类，若无则使用数簿智能分类
        val categoryName = when {
            alipayCategory.contains("餐饮") || alipayCategory.contains("美食") -> "食"
            alipayCategory.contains("交通") || alipayCategory.contains("出行") || alipayCategory.contains("乘车") -> "行"
            alipayCategory.contains("服饰") || alipayCategory.contains("穿戴") || alipayCategory.contains("美容") -> "衣"
            alipayCategory.contains("住房") || alipayCategory.contains("物业") || alipayCategory.contains("水电") || alipayCategory.contains("充值缴费") -> "住"
            type == TransactionType.INCOME && alipayCategory.contains("红包") -> "收红包"
            type == TransactionType.INCOME && alipayCategory.contains("工资") -> "工资"
            else -> AutoRecordAccessibilityService.classifyCategory("$alipayCategory $merchantName $remarkText", type)
        }

        val orderId = row["交易订单号"]?.trim()?.takeIf { it.isNotBlank() && it != "/" }
            ?: row["交易号"]?.trim()?.takeIf { it.isNotBlank() && it != "/" }

        return ParsedBill(
            timestamp = timestamp,
            type = type,
            amount = amount,
            categoryName = categoryName,
            accountName = accountName,
            merchant = merchantName,
            remark = remarkText,
            orderId = orderId
        )
    }

    private fun parseUnionPayRow(row: Map<String, String>): ParsedBill? {
        val timeStr = row["交易时间"]
        val timestamp = parseTimestamp(timeStr) ?: return null

        val status = row["交易状态"]?.trim() ?: ""
        if (status.contains("退款") || status.contains("撤销") || status.contains("失败")) {
            return null
        }

        val typeStr = row["收/支"]?.trim() ?: row["交易类型"]?.trim() ?: ""
        val type = when {
            typeStr.contains("支出") || typeStr.contains("消费") || typeStr.contains("付款") -> TransactionType.EXPENSE
            typeStr.contains("收入") || typeStr.contains("收款") || typeStr.contains("入账") -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }

        val amount = parseAmount(row["交易金额"] ?: row["交易金额(元)"] ?: row["金额"]) ?: return null
        if (amount <= 0.0) return null

        val merchant = row["交易商户/对方"] ?: row["交易商户"] ?: row["商户名称"] ?: row["交易对方"] ?: "云闪付消费"
        val desc = row["交易说明"] ?: row["交易类型"] ?: ""
        val card = row["支付卡号"] ?: row["交易卡号"] ?: row["交易方式"] ?: "银行卡"

        val bankNames = listOf(
            "招商银行", "工商银行", "建设银行", "农业银行", "中国银行", "交通银行",
            "平安银行", "中信银行", "浦发银行", "民生银行", "光大银行", "广发银行",
            "兴业银行", "华夏银行", "北京银行", "上海银行", "邮储银行"
        )
        val accountName = bankNames.firstOrNull { card.contains(it) } ?: "云闪付"

        val categoryName = AutoRecordAccessibilityService.classifyCategory("$merchant $desc", type)
        val orderId = row["交易流水号"] ?: row["流水号"] ?: row["参考号"]

        return ParsedBill(
            timestamp = timestamp,
            type = type,
            amount = amount,
            categoryName = categoryName,
            accountName = accountName,
            merchant = merchant.trim(),
            remark = desc.trim(),
            orderId = orderId?.trim()?.takeIf { it.isNotBlank() && it != "/" }
        )
    }

    private fun parseShubuRow(row: Map<String, String>): ParsedBill? {
        val timeStr = row["时间"]
        val timestamp = parseTimestamp(timeStr) ?: return null
        val type = if (row["类型"]?.contains("收入") == true) TransactionType.INCOME else TransactionType.EXPENSE
        val amount = parseAmount(row["金额"]) ?: return null
        val catName = row["分类"] ?: if (type == TransactionType.INCOME) "其它" else "其他"
        val accName = row["账户"] ?: "微信"
        val remark = row["备注"] ?: ""
        val tag = row["标签"] ?: ""

        // 尝试从备注中解析历史已存的 [单号:xxx]
        val orderId = Regex("""\[单号:([^\]]+)\]""").find(remark)?.groupValues?.getOrNull(1)

        return ParsedBill(
            timestamp = timestamp,
            type = type,
            amount = amount,
            categoryName = catName,
            accountName = accName,
            merchant = remark,
            remark = remark,
            orderId = orderId,
            tag = tag
        )
    }

    // ==================== 5. 主导入引擎与幂等入库 ====================

    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        targetBookId: Long
    ): ImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        try {
            val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                decodeStreamToString(inputStream)
            } ?: return@withContext ImportResult(success = false, message = "无法读取选取的账单文件内容")

            val lines = content.lines().map { it.trimEnd('\r') }
            if (lines.isEmpty()) {
                return@withContext ImportResult(success = false, message = "文件内容为空")
            }

            val sniffResult = sniffFormatAndHeader(lines)
                ?: return@withContext ImportResult(
                    success = false,
                    message = "未能识别该文件格式，请确保上传微信、支付宝、云闪付导出的官方 CSV 账单"
                )

            val parsedBills = mutableListOf<ParsedBill>()
            var filteredCount = 0

            // 从表头下一行开始逐行解析数据
            for (i in (sniffResult.headerIndex + 1) until lines.size) {
                val rawLine = lines[i].trim()
                if (rawLine.isEmpty() || rawLine.startsWith("-") || rawLine.startsWith("=")) continue

                try {
                    val cols = parseCsvLine(rawLine)
                    if (cols.isEmpty()) continue

                    // 填充列对齐
                    val padded = cols.toMutableList()
                    while (padded.size < sniffResult.headers.size) padded.add("")
                    val rowMap = sniffResult.headers.zip(padded).toMap()

                    val bill = when (sniffResult.format) {
                        BillFormat.WECHAT -> parseWeChatRow(rowMap)
                        BillFormat.ALIPAY -> parseAlipayRow(rowMap)
                        BillFormat.UNIONPAY -> parseUnionPayRow(rowMap)
                        BillFormat.SHUBU -> parseShubuRow(rowMap)
                        BillFormat.UNKNOWN -> null
                    }

                    if (bill != null) {
                        parsedBills.add(bill)
                    } else {
                        filteredCount++
                    }
                } catch (e: Exception) {
                    errors.add("第 ${i + 1} 行解析异常: ${e.message}")
                }
            }

            if (parsedBills.isEmpty()) {
                return@withContext ImportResult(
                    success = false,
                    format = sniffResult.format,
                    filteredCount = filteredCount,
                    failedCount = errors.size,
                    message = "未在文件中找到有效的交易记录（可能均为退款或不计收支流水）"
                )
            }

            // ==================== 批量去重与原子入库 ====================
            val db = AppDatabase.getDatabase(context, this)

            // 1. 获取现有数据库中所有带单号的记录，构筑单号黑名单哈希集合
            val existingRemarks = db.recordDao().getAllRemarksWithOrderId()
            val existingOrderIds = HashSet<String>(existingRemarks.size)
            val orderIdRegex = Regex("""\[单号:([^\]]+)\]""")
            for (remark in existingRemarks) {
                orderIdRegex.find(remark)?.groupValues?.getOrNull(1)?.let {
                    existingOrderIds.add(it.trim())
                }
            }

            // 2. 获取现有记录的时间戳金额签名（用于无单号的精确兜底去重）
            val existingSignatures = HashSet<String>(db.recordDao().getAllRecordSignatures())

            val recordsToInsert = mutableListOf<Record>()
            var duplicateCount = 0

            for (bill in parsedBills) {
                val isDup = if (!bill.orderId.isNullOrBlank()) {
                    bill.orderId in existingOrderIds
                } else {
                    val sig = "${bill.timestamp}_${bill.amount}_${bill.type.name}"
                    sig in existingSignatures
                }

                if (isDup) {
                    duplicateCount++
                    continue
                }

                // 将单号打入待入库集合，杜绝同一 CSV 内出现两笔相同单号互撞
                if (!bill.orderId.isNullOrBlank()) {
                    existingOrderIds.add(bill.orderId)
                }

                // 组装最终备注：若包含外部订单号，规整为 "商户/说明 [单号:xxx]"
                val finalRemark = if (!bill.orderId.isNullOrBlank()) {
                    val cleanRemark = bill.remark.ifBlank { bill.merchant }
                    if (cleanRemark.contains("[单号:")) cleanRemark else "$cleanRemark [单号:${bill.orderId}]"
                } else {
                    bill.remark.ifBlank { bill.merchant }
                }

                recordsToInsert.add(
                    Record(
                        bookId = targetBookId,
                        type = bill.type,
                        amount = bill.amount,
                        categoryName = bill.categoryName,
                        accountName = bill.accountName,
                        remark = finalRemark,
                        tag = bill.tag,
                        timestamp = bill.timestamp
                    )
                )
            }

            // 3. 自动补全账本资产账户（若出现新银行卡或新账户，自动创建）
            try {
                val distinctAccounts = recordsToInsert.map { it.accountName }.distinct()
                for (acc in distinctAccounts) {
                    if (db.accountDao().getAccountByName(acc) == null) {
                        db.accountDao().insertAccount(Account(name = acc, balance = 0.0, currency = "CNY"))
                    }
                }
            } catch (_: Exception) {}

            // 4. 批量事务入库
            if (recordsToInsert.isNotEmpty()) {
                db.recordDao().insertRecords(recordsToInsert)
            }

            val summaryMsg = "✅ 成功导入 ${recordsToInsert.size} 笔账单！\n" +
                    "来源：${sniffResult.format.displayName}\n" +
                    "跳过重复：$duplicateCount 笔\n" +
                    "过滤中性/退款：$filteredCount 笔"

            ImportResult(
                success = true,
                format = sniffResult.format,
                totalCount = parsedBills.size + filteredCount,
                importedCount = recordsToInsert.size,
                duplicateCount = duplicateCount,
                filteredCount = filteredCount,
                failedCount = errors.size,
                message = summaryMsg,
                errors = errors
            )
        } catch (e: Exception) {
            ImportResult(
                success = false,
                message = "导入执行失败: ${e.message ?: "未知异常"}"
            )
        }
    }
}
