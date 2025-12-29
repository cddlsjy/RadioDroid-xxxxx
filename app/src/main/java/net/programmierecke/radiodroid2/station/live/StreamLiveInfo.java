package net.programmierecke.radiodroid2.station.live;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import net.programmierecke.radiodroid2.BuildConfig;

import java.util.Map;

public class StreamLiveInfo implements Parcelable {

    final private String TAG = "StreamLiveInfo";

    public StreamLiveInfo(Map<String, String> rawMetadata) {
        this.rawMetadata = rawMetadata;

        // 总是输出调试信息，以便诊断乱码问题
        Log.i(TAG, "=== StreamLiveInfo构造函数开始 ===");
        Log.i(TAG, "StreamLiveInfo构造函数 - 原始元数据: " + rawMetadata);

        if (rawMetadata != null && rawMetadata.containsKey("StreamTitle")) {
            title = rawMetadata.get("StreamTitle");

            Log.i(TAG, "StreamLiveInfo构造函数 - StreamTitle: " + title);
            Log.i(TAG, "StreamLiveInfo构造函数 - StreamTitle长度: " + (title != null ? title.length() : 0));
            
            // 尝试检测和修复编码问题
            if (title != null && !title.isEmpty()) {
                title = fixEncodingIssues(title);
                Log.i(TAG, "StreamLiveInfo构造函数 - 修复编码后的StreamTitle: " + title);
            }
            
            // 打印标题中每个字符的Unicode值
            if (title != null && !title.isEmpty()) {
                StringBuilder charCodes = new StringBuilder();
                for (int i = 0; i < Math.min(title.length(), 100); i++) {
                    char c = title.charAt(i);
                    charCodes.append(String.format("'%c'(%04X) ", c, (int) c));
                }
                Log.i(TAG, "StreamLiveInfo构造函数 - StreamTitle的前100个字符的Unicode值: " + charCodes.toString());
            }
            
            // 打印标题的字节表示（用于调试编码问题）
            if (title != null && !title.isEmpty()) {
                try {
                    byte[] bytes = title.getBytes("UTF-8");
                    StringBuilder byteCodes = new StringBuilder();
                    for (int i = 0; i < Math.min(bytes.length, 100); i++) {
                        byteCodes.append(String.format("%02X ", bytes[i] & 0xFF));
                    }
                    Log.i(TAG, "StreamLiveInfo构造函数 - StreamTitle的前100个字节的UTF-8编码: " + byteCodes.toString());
                } catch (Exception e) {
                    Log.e(TAG, "StreamLiveInfo构造函数 - 获取字节编码时出错: " + e.getMessage());
                }
            }

            if (!TextUtils.isEmpty(title)) {
                // 尝试智能解析元数据
                Log.i(TAG, "StreamLiveInfo构造函数 - 开始解析元数据");
                parseMetadataIntelligently(title);

                Log.i(TAG, "StreamLiveInfo构造函数 - 解析完成");
                Log.i(TAG, "StreamLiveInfo构造函数 - 最终艺术家: '" + artist + "'");
                Log.i(TAG, "StreamLiveInfo构造函数 - 最终曲目: '" + track + "'");
                Log.i(TAG, "=== StreamLiveInfo构造函数结束 ===");
            } else if (title != null && title.isEmpty()) {
                // 处理空字符串情况（可能是检测到数据损坏）
                Log.w(TAG, "StreamLiveInfo构造函数 - 检测到空标题，可能是数据损坏");
                // 保持artist和track为空字符串，不进行解析
            }
        } else {
            Log.w(TAG, "StreamLiveInfo构造函数 - 原始元数据为空或不包含StreamTitle");
        }
    }

    /**
     * 将字节数组转换为十六进制字符串（用于调试）
     * @param bytes 字节数组
     * @param maxBytes 最大显示字节数
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes, int maxBytes) {
        if (bytes == null || bytes.length == 0) {
            return "[]";
        }
        
        StringBuilder hexString = new StringBuilder();
        int limit = Math.min(bytes.length, maxBytes);
        
        for (int i = 0; i < limit; i++) {
            hexString.append(String.format("%02X ", bytes[i] & 0xFF));
        }
        
        if (bytes.length > maxBytes) {
            hexString.append("...");
        }
        
        return hexString.toString();
    }

    /**
     * 尝试修复编码问题，特别是UTF-8被错误地用GBK解码的情况
     * @param input 可能存在编码问题的字符串
     * @return 修复后的字符串
     */
    private String fixEncodingIssues(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        try {
            // 检测是否包含可能的乱码字符
            // 乱码通常包含一些特定的Unicode字符范围
            boolean hasGarbledChars = false;
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                // 检查是否在常见的乱码字符范围内
                if ((c >= 0x8080 && c <= 0x8FFF) || 
                    (c >= 0x9000 && c <= 0x9FFF) ||
                    (c >= 0xE000 && c <= 0xF8FF) ||
                    (c >= 0xF900 && c <= 0xFAFF)) {
                    hasGarbledChars = true;
                    break;
                }
            }
            
            if (!hasGarbledChars) {
                // 如果没有明显的乱码字符，直接返回原字符串
                return input;
            }
            
            // 尝试修复：将字符串按GBK编码为字节，然后按UTF-8解码
            byte[] gbkBytes = input.getBytes("GBK");
            String fixedString = new String(gbkBytes, "UTF-8");
            
            // 检查修复后的字符串是否合理
            if (isReasonableString(fixedString)) {
                Log.i(TAG, "fixEncodingIssues - 成功修复编码问题");
                Log.i(TAG, "fixEncodingIssues - 原始字符串: " + input);
                Log.i(TAG, "fixEncodingIssues - 修复后字符串: " + fixedString);
                return fixedString;
            }
            
            // 如果上述方法不成功，尝试其他编码组合
            byte[] isoBytes = input.getBytes("ISO-8859-1");
            String fixedString2 = new String(isoBytes, "UTF-8");
            
            if (isReasonableString(fixedString2)) {
                Log.i(TAG, "fixEncodingIssues - 使用ISO-8859-1到UTF-8成功修复编码问题");
                Log.i(TAG, "fixEncodingIssues - 原始字符串: " + input);
                Log.i(TAG, "fixEncodingIssues - 修复后字符串: " + fixedString2);
                return fixedString2;
            }
            
            Log.i(TAG, "fixEncodingIssues - 无法自动修复编码问题，返回原始字符串");
            return input;
            
        } catch (Exception e) {
            Log.e(TAG, "fixEncodingIssues - 修复编码时出错: " + e.getMessage());
            return input;
        }
    }
    
    /**
     * 检查字符串是否"合理"（即不太可能是乱码）
     * @param str 要检查的字符串
     * @return 如果字符串合理返回true，否则返回false
     */
    private boolean isReasonableString(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        
        // 检查是否包含中文字符
        int chineseCharCount = 0;
        int totalCharCount = str.length();
        
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            // 中文字符的Unicode范围
            if ((c >= 0x4E00 && c <= 0x9FFF) || 
                (c >= 0x3400 && c <= 0x4DBF) ||
                (c >= 0x20000 && c <= 0x2A6DF) ||
                (c >= 0x2A700 && c <= 0x2B73F) ||
                (c >= 0x2B740 && c <= 0x2B81F) ||
                (c >= 0x2B820 && c <= 0x2CEAF) ||
                (c >= 0xF900 && c <= 0xFAFF) ||
                (c >= 0x2F800 && c <= 0x2FA1F)) {
                chineseCharCount++;
            }
        }
        
        // 如果中文字符占比超过30%，认为是合理的中文文本
        double chineseRatio = (double) chineseCharCount / totalCharCount;
        if (chineseRatio > 0.3) {
            Log.i(TAG, "isReasonableString - 中文字符占比: " + chineseRatio + ", 认为是合理的中文文本");
            return true;
        }
        
        // 检查是否包含常见的英文单词字符
        int englishCharCount = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == ' ') {
                englishCharCount++;
            }
        }
        
        // 如果英文字符占比超过70%，认为是合理的英文文本
        double englishRatio = (double) englishCharCount / totalCharCount;
        if (englishRatio > 0.7) {
            Log.i(TAG, "isReasonableString - 英文字符占比: " + englishRatio + ", 认为是合理的英文文本");
            return true;
        }
        
        Log.i(TAG, "isReasonableString - 中文字符占比: " + chineseRatio + ", 英文字符占比: " + englishRatio + ", 认为不是合理的文本");
        return false;
    }

    public @NonNull
    String getTitle() {
        return title;
    }

    public
    boolean hasArtistAndTrack() {
        return ! (artist.isEmpty() || track.isEmpty());
    }

    public @NonNull
    String getArtist() {
        return artist;
    }

    public @NonNull
    String getTrack() {
        return track;
    }

    public Map<String, String> getRawMetadata() {
        return rawMetadata;
    }

    private String title = "";
    private String artist = "";
    private String track = "";
    private Map<String, String> rawMetadata;

    protected StreamLiveInfo(Parcel in) {
        title = in.readString();
        artist = in.readString();
        track = in.readString();
        in.readMap(rawMetadata, String.class.getClassLoader());
    }

    public static final Creator<StreamLiveInfo> CREATOR = new Creator<StreamLiveInfo>() {
        @Override
        public StreamLiveInfo createFromParcel(Parcel in) {
            return new StreamLiveInfo(in);
        }

        @Override
        public StreamLiveInfo[] newArray(int size) {
            return new StreamLiveInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * 智能解析元数据，处理不同格式的电台信息
     * @param title 原始标题字符串
     */
    /**
     * 智能元信息解析，支持多种格式和极端情况
     * 使用多阶段解析策略，优先处理常见格式，然后处理特殊格式
     */
    private void parseMetadataIntelligently(String title) {
        Log.i(TAG, "=== parseMetadataIntelligently开始 ===");
        Log.i(TAG, "parseMetadataIntelligently - 原始标题: " + title);
        
        // 默认值
        artist = "";
        track = "";
        
        // 预处理标题
        String originalTitle = title;
        title = preprocessTitle(title);
        Log.i(TAG, "parseMetadataIntelligently - 预处理后标题: " + title);
        
        if (title.isEmpty()) {
            Log.w(TAG, "parseMetadataIntelligently - 预处理后标题为空，返回");
            return;
        }
        
        // 检查是否包含过多问号，如果是则忽略此元数据
        if (isMetadataCorrupted(title)) {
            Log.w(TAG, "parseMetadataIntelligently - 检测到元数据损坏，忽略此元数据");
            return;
        }
        
        // 第一阶段：处理字段格式（如title=, artist=, text=等）
        Log.i(TAG, "parseMetadataIntelligently - 尝试字段格式解析");
        if (parseFieldFormats(title)) {
            Log.i(TAG, "parseMetadataIntelligently - 字段格式解析成功 - 艺术家: '" + artist + "', 歌曲: '" + track + "'");
            // 清理解析结果
            cleanParsingResults();
            Log.i(TAG, "=== parseMetadataIntelligently结束 ===");
            return;
        }
        
        // 第二阶段：处理标准格式和常见变体
        Log.i(TAG, "parseMetadataIntelligently - 尝试标准格式解析");
        if (parseStandardFormats(title)) {
            Log.i(TAG, "parseMetadataIntelligently - 标准格式解析成功 - 艺术家: '" + artist + "', 歌曲: '" + track + "'");
            // 清理解析结果
            cleanParsingResults();
            Log.i(TAG, "=== parseMetadataIntelligently结束 ===");
            return;
        }
        
        // 第三阶段：处理特殊格式和极端情况
        Log.i(TAG, "parseMetadataIntelligently - 尝试特殊格式解析");
        if (parseSpecialFormats(title)) {
            Log.i(TAG, "parseMetadataIntelligently - 特殊格式解析成功 - 艺术家: '" + artist + "', 歌曲: '" + track + "'");
            // 清理解析结果
            cleanParsingResults();
            Log.i(TAG, "=== parseMetadataIntelligently结束 ===");
            return;
        }
        
        // 第四阶段：作为最后手段，尝试智能分割
        Log.i(TAG, "parseMetadataIntelligently - 尝试最后手段解析");
        parseAsLastResort(title);
        Log.i(TAG, "parseMetadataIntelligently - 最后手段解析完成 - 艺术家: '" + artist + "', 歌曲: '" + track + "'");
        
        // 清理结果
        cleanParsingResults();
        
        Log.i(TAG, "parseMetadataIntelligently - 最终解析结果 - 艺术家: '" + artist + "', 歌曲: '" + track + "'");
        Log.i(TAG, "=== parseMetadataIntelligently结束 ===");
    }
    
    /**
     * 从包含text字段的字符串中提取歌曲名
     * @param input 包含text字段的字符串
     * @return 提取的歌曲名
     */
    private String extractTextValue(String input) {
        // 查找text字段
        int textIndex = input.indexOf("text=");
        if (textIndex == -1) {
            return input;
        }
        
        // 跳过"text="
        int valueStart = textIndex + 5;
        
        // 检查是否有引号
        if (valueStart < input.length() && input.charAt(valueStart) == '"') {
            // 找到匹配的结束引号
            int valueEnd = input.indexOf('"', valueStart + 1);
            if (valueEnd != -1) {
                return input.substring(valueStart + 1, valueEnd);
            }
        }
        
        // 如果没有引号，查找下一个空格或字段结束
        int valueEnd = input.indexOf(' ', valueStart);
        if (valueEnd == -1) {
            valueEnd = input.length();
        }
        
        return input.substring(valueStart, valueEnd);
    }
    
    /**
     * 从包含指定字段的字符串中提取字段值
     * @param input 包含字段的字符串
     * @param fieldName 字段名（如"title"或"artist"）
     * @return 提取的字段值
     */
    private String extractFieldValue(String input, String fieldName) {
        Log.i(TAG, "=== extractFieldValue开始 ===");
        Log.i(TAG, "extractFieldValue - 输入字符串: " + input);
        Log.i(TAG, "extractFieldValue - 字段名: " + fieldName);
        
        // 构建字段模式，如title="
        String fieldPattern = fieldName + "=\"";
        Log.i(TAG, "extractFieldValue - 查找模式: " + fieldPattern);
        
        int fieldIndex = input.indexOf(fieldPattern);
        Log.i(TAG, "extractFieldValue - 字段位置: " + fieldIndex);
        
        if (fieldIndex == -1) {
            Log.i(TAG, "extractFieldValue - 未找到字段，返回空字符串");
            return "";
        }
        
        // 跳过字段名和等号和引号
        int valueStart = fieldIndex + fieldPattern.length();
        Log.i(TAG, "extractFieldValue - 值开始位置: " + valueStart);
        
        // 找到匹配的结束引号
        int valueEnd = input.indexOf('"', valueStart);
        Log.i(TAG, "extractFieldValue - 值结束位置: " + valueEnd);
        
        if (valueEnd != -1) {
            String extractedValue = input.substring(valueStart, valueEnd);
            Log.i(TAG, "extractFieldValue - 提取的值: '" + extractedValue + "'");
            return extractedValue;
        }
        
        // 如果没有找到结束引号，返回空字符串
        Log.i(TAG, "extractFieldValue - 未找到结束引号，返回空字符串");
        return "";
    }
    
    /**
     * 从包含text字段的字符串中提取歌曲名（改进版本）
     * @param input 包含text字段的字符串
     * @return 提取的歌曲名
     */
    private String extractTextValueImproved(String input) {
        // 查找text字段
        int textIndex = input.indexOf("text=");
        if (textIndex == -1) {
            return "";
        }
        
        // 跳过"text="
        int valueStart = textIndex + 5;
        
        // 检查是否有引号
        if (valueStart < input.length() && input.charAt(valueStart) == '"') {
            // 找到匹配的结束引号
            int valueEnd = input.indexOf('"', valueStart + 1);
            if (valueEnd != -1) {
                return input.substring(valueStart + 1, valueEnd);
            }
        }
        
        // 如果没有引号，查找下一个空格或字段结束
        int valueEnd = input.indexOf(' ', valueStart);
        if (valueEnd == -1) {
            valueEnd = input.length();
        }
        
        return input.substring(valueStart, valueEnd);
    }
    
    /**
     * 检查元数据是否损坏
     * @param title 要检查的标题
     * @return 如果元数据损坏返回true，否则返回false
     */
    private boolean isMetadataCorrupted(String title) {
        if (title == null || title.isEmpty()) {
            return false;
        }
        
        // 计算问号字符的数量
        int questionMarkCount = 0;
        for (int i = 0; i < title.length(); i++) {
            if (title.charAt(i) == '?') {
                questionMarkCount++;
            }
        }
        
        // 计算控制字符的数量
        int controlCharCount = 0;
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r') {
                controlCharCount++;
            }
        }
        
        // 计算问号和控制字符的比例
        double questionMarkRatio = (double) questionMarkCount / title.length();
        double controlCharRatio = (double) controlCharCount / title.length();
        
        // 放宽损坏检测标准：
        // 1. 问号比例超过50%才认为损坏（之前是30%）
        // 2. 控制字符比例超过20%才认为损坏（之前是10%）
        // 3. 只有在标题很短（小于10字符）且包含多个问号（大于等于2个）时才认为损坏
        boolean isCorrupted = (questionMarkRatio > 0.5) || 
                             (controlCharRatio > 0.2) || 
                             (questionMarkCount >= 2 && title.length() < 10);
        
        if (BuildConfig.DEBUG && isCorrupted) {
            Log.d(TAG, "isMetadataCorrupted - 检测到损坏的元数据: '" + title + 
                  "', 问号数量: " + questionMarkCount + 
                  ", 控制字符数量: " + controlCharCount +
                  ", 总长度: " + title.length() + 
                  ", 问号比例: " + String.format("%.2f", questionMarkRatio) +
                  ", 控制字符比例: " + String.format("%.2f", controlCharRatio));
        } else if (BuildConfig.DEBUG && questionMarkCount > 0) {
            Log.d(TAG, "isMetadataCorrupted - 元数据包含问号但未达到损坏阈值: '" + title + 
                  "', 问号数量: " + questionMarkCount + 
                  ", 总长度: " + title.length() + 
                  ", 问号比例: " + String.format("%.2f", questionMarkRatio));
        }
        
        return isCorrupted;
    }
    
    /**
     * 预处理标题，移除无效字符和标准化格式
     */
    private String preprocessTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "";
        }
        
        // 移除控制字符（保留常见的空白字符）
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r' || !Character.isISOControl(c)) {
                cleaned.append(c);
            }
        }
        
        String result = cleaned.toString().trim();
        
        // 处理一些常见的格式问题
        result = result.replaceAll("^\\s*[-=]+\\s*", ""); // 移除开头的-或=
        result = result.replaceAll("\\s*[-=]+\\s*$", ""); // 移除结尾的-或=
        result = result.replaceAll("\\s+", " "); // 标准化空白字符
        
        return result;
    }
    
    /**
     * 解析标准格式和常见变体
     */
    private boolean parseStandardFormats(String title) {
        // 如果包含字段格式，不使用标准格式解析
        if (title.contains("text=") || title.contains("title=") || title.contains("artist=")) {
            Log.i(TAG, "parseStandardFormats - 检测到字段格式，跳过标准格式解析");
            return false;
        }
        
        // 标准格式 "艺术家 - 歌曲名"
        if (title.contains(" - ")) {
            String[] parts = title.split(" - ", 2);
            artist = parts[0].trim();
            track = parts[1].trim();
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseStandardFormats - 使用标准格式解析");
            }
            return true;
        }
        
        // 包含"+"分隔符的格式（处理一些特殊格式）
        if (title.contains("+")) {
            return parsePlusSeparatedFormat(title);
        }
        
        return false;
    }
    
    /**
     * 解析包含+分隔符的格式
     */
    private boolean parsePlusSeparatedFormat(String title) {
        // 将所有"+"替换为空格
        String normalizedTitle = title.replace("+", " ");
        
        // 使用"-"作为艺术家和歌曲名的分隔符
        if (normalizedTitle.contains(" - ") || normalizedTitle.contains("-")) {
            String[] parts = normalizedTitle.split(" - ", 2);
            if (parts.length == 1) {
                // 如果用" - "分割失败，尝试用"-"分割
                parts = normalizedTitle.split("-", 2);
            }
            
            if (parts.length >= 2) {
                artist = parts[0].trim();
                track = parts[1].trim();
                
                // 检查并移除重复的艺术家名称
                if (track.startsWith(artist)) {
                    track = track.substring(artist.length()).trim();
                }
                
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "parsePlusSeparatedFormat - 使用+替换为空格，-作为分隔符解析");
                }
                return true;
            }
        }
        
        // 如果没有"-"分隔符，整个字符串作为歌曲名
        artist = "";
        track = normalizedTitle;
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "parsePlusSeparatedFormat - 使用+替换为空格，但没有-分隔符");
        }
        return true;
    }
    
    /**
     * 解析特殊格式和极端情况
     */
    private boolean parseSpecialFormats(String title) {
        // 处理空艺术家字段的情况：" - 歌曲名"
        if (title.startsWith(" - ")) {
            String remaining = title.substring(3).trim();
            if (!remaining.isEmpty()) {
                artist = "未知艺术家";
                track = remaining;
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "parseSpecialFormats - 检测到空艺术家字段，艺术家设为: " + artist + ", 歌曲名: " + track);
                }
                return true;
            }
        }
        
        // 处理只有歌曲名的情况
        if (!title.contains(" - ") && !title.contains("=") && !title.contains("+")) {
            artist = "";
            track = title;
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseSpecialFormats - 检测到只有歌曲名的情况");
            }
            return true;
        }
        
        // 处理包含多个分隔符的情况
        if (title.split(" - ").length > 2) {
            String[] parts = title.split(" - ", 3);
            if (parts.length >= 3) {
                // 可能是 "艺术家 - 专辑 - 歌曲名" 格式
                artist = parts[0].trim();
                track = parts[2].trim(); // 取最后一部分作为歌曲名
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "parseSpecialFormats - 检测到多分隔符格式，可能是艺术家-专辑-歌曲名");
                }
                return true;
            }
        }
        
        // 处理包含HTML实体或特殊字符的情况
        if (title.contains("&amp;") || title.contains("&quot;") || title.contains("&lt;") || title.contains("&gt;")) {
            return parseWithHtmlEntities(title);
        }
        
        // 处理包含过多空白字符的情况
        if (title.matches(".*\\s{3,}.*")) {
            return parseWithExcessiveWhitespace(title);
        }
        
        // 处理包含URL或链接的情况
        if (title.matches(".*https?://.*") || title.matches(".*www\\..*")) {
            return parseWithUrl(title);
        }
        
        // 处理包含非标准字符的情况
        if (title.matches(".*[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F].*")) {
            return parseWithControlCharacters(title);
        }
        
        return false;
    }
    
    /**
     * 解析字段格式（如text=, title=等）
     */
    private boolean parseFieldFormats(String title) {
        Log.i(TAG, "=== parseFieldFormats开始 ===");
        Log.i(TAG, "parseFieldFormats - 输入标题: " + title);
        Log.i(TAG, "parseFieldFormats - 标题长度: " + title.length());
        
        // 记录标题的字节表示
        try {
            byte[] titleBytes = title.getBytes("UTF-8");
            Log.i(TAG, "parseFieldFormats - 标题UTF-8字节长度: " + titleBytes.length);
            Log.i(TAG, "parseFieldFormats - 标题UTF-8字节前20: " + bytesToHex(titleBytes, 20));
        } catch (Exception e) {
            Log.e(TAG, "parseFieldFormats - 字节转换错误: " + e.getMessage());
        }
        
        // 包含text字段的格式
        if (title.contains("text=")) {
            Log.i(TAG, "parseFieldFormats - 检测到text字段，调用parseTextFieldFormat");
            return parseTextFieldFormat(title);
        }
        
        // 包含title和artist字段的格式
        if (title.contains("title=") && title.contains("artist=")) {
            Log.i(TAG, "parseFieldFormats - 检测到title和artist字段");
            
            String extractedArtist = extractFieldValue(title, "artist");
            String extractedTrack = extractFieldValue(title, "title");
            
            Log.i(TAG, "parseFieldFormats - 提取的artist原始值: '" + extractedArtist + "'");
            Log.i(TAG, "parseFieldFormats - 提取的track原始值: '" + extractedTrack + "'");
            
            artist = extractedArtist;
            track = extractedTrack;
            
            Log.i(TAG, "parseFieldFormats - 最终artist: '" + artist + "'");
            Log.i(TAG, "parseFieldFormats - 最终track: '" + track + "'");
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseFieldFormats - 使用title/artist字段格式解析");
            }
            return true;
        }
        
        Log.i(TAG, "parseFieldFormats - 未检测到已知的字段格式");
        return false;
    }
    
    /**
     * 解析包含text字段的格式
     */
    private boolean parseTextFieldFormat(String title) {
        Log.i(TAG, "=== parseTextFieldFormat开始 ===");
        Log.i(TAG, "parseTextFieldFormat - 输入标题: " + title);
        
        // 尝试从text字段前提取艺术家
        int textIndex = title.indexOf("text=");
        Log.i(TAG, "parseTextFieldFormat - text=位置: " + textIndex);
        
        if (textIndex > 0) {
            // 查找text字段前的艺术家名称
            String beforeText = title.substring(0, textIndex).trim();
            Log.i(TAG, "parseTextFieldFormat - text字段前内容: '" + beforeText + "'");
            
            if (beforeText.endsWith(" - ")) {
                artist = beforeText.substring(0, beforeText.length() - 3).trim();
                Log.i(TAG, "parseTextFieldFormat - 从'text - '格式提取艺术家: '" + artist + "'");
            } else {
                artist = beforeText;
                Log.i(TAG, "parseTextFieldFormat - 直接使用text前内容作为艺术家: '" + artist + "'");
            }
        } else {
            Log.i(TAG, "parseTextFieldFormat - text字段位于开头，无法从前缀提取艺术家");
            artist = "";
        }
        
        // 使用改进的text值提取方法
        Log.i(TAG, "parseTextFieldFormat - 调用extractTextValueImproved提取track");
        track = extractTextValueImproved(title);
        Log.i(TAG, "parseTextFieldFormat - extractTextValueImproved结果: '" + track + "'");
        
        // 如果提取的track为空，则尝试使用原始方法
        if (track.isEmpty()) {
            Log.i(TAG, "parseTextFieldFormat - extractTextValueImproved结果为空，尝试原始方法");
            track = extractTextValue(title);
            Log.i(TAG, "parseTextFieldFormat - extractTextValue结果: '" + track + "'");
        }
        
        // 处理特殊情况：如果艺术家为空且track不为空，可能是类似text="WEGR BASIC ID_6"的情况
        if ((artist == null || artist.trim().isEmpty()) && !track.isEmpty()) {
            // 检查是否是特殊标识模式（如电台ID、法律ID、广告块等）
            if (track.matches(".*\\b(ID|LEGAL|SPOT|PROMO|COMMERCIAL|ADVERTISEMENT|BLOCK)\\b.*") || 
                track.matches(".*\\b\\d{2}_.*") || // 匹配类似"04_"的模式
                track.matches(".*[A-Z]{3,4}\\s*-\\s*[A-Z]+.*") || // 匹配类似"WEGR - LEGAL"的模式
                track.matches(".*\\bSPOT\\s+BLOCK\\s+END\\b.*") || // 匹配"Spot Block End"
                track.matches(".*\\bSPOT\\s+BLOCK\\b.*")) { // 匹配"Spot Block"
                // 如果是特殊标识模式，不进行分割，整个内容作为曲目
                Log.i(TAG, "parseTextFieldFormat - 检测到特殊标识模式，不进行分割");
                artist = "未知艺术家";
                Log.i(TAG, "parseTextFieldFormat - 设置艺术家为: '" + artist + "', 曲目保持为: '" + track + "'");
            }
            // 检查track是否包含多个单词，如果是，则可能需要分割
            else if (track.split("\\s+").length > 2) {
                // 如果track包含多个单词（超过2个），可能是电台名称+节目ID
                // 尝试分割：前两个单词作为艺术家，其余作为曲目
                String[] words = track.split("\\s+", 3);
                if (words.length >= 3) {
                    artist = words[0] + " " + words[1];
                    track = words[2];
                    Log.i(TAG, "parseTextFieldFormat - 检测到多词track，分割为艺术家: '" + artist + "', 曲目: '" + track + "'");
                } else if (words.length == 2) {
                    artist = words[0];
                    track = words[1];
                    Log.i(TAG, "parseTextFieldFormat - 检测到两词track，分割为艺术家: '" + artist + "', 曲目: '" + track + "'");
                }
            } else {
                Log.i(TAG, "parseTextFieldFormat - 艺术家为空但track不为空，设置为'未知艺术家'");
                artist = "未知艺术家";
            }
        }
        
        Log.i(TAG, "parseTextFieldFormat - 最终artist: '" + artist + "'");
        Log.i(TAG, "parseTextFieldFormat - 最终track: '" + track + "'");
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "parseTextFieldFormat - 使用text字段格式解析");
        }
        return true;
    }
    
    /**
     * 作为最后手段的智能分割
     */
    private void parseAsLastResort(String title) {
        // 首先尝试使用各种分隔符进行智能分割
        String[] separators = {" - ", "-", " | ", "|", " / ", "/", ":"};
        
        for (String separator : separators) {
            if (title.contains(separator)) {
                String[] parts = title.split(separator, 2);
                if (parts.length >= 2) {
                    artist = parts[0].trim();
                    track = parts[1].trim();
                    
                    // 检查分割后的合理性
                    if (artist.length() > 0 && track.length() > 0 && 
                        artist.length() < 50 && track.length() < 100) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "parseAsLastResort - 使用分隔符 '" + separator + "' 成功分割");
                        }
                        return;
                    }
                }
            }
        }
        
        // 尝试基于大写字母的分割（假设艺术家和歌曲名都以大写字母开头）
        if (title.matches(".*[A-Z].*[A-Z].*")) {
            String[] words = title.split("\\s+");
            // 只有当单词数量足够多时才尝试分割（至少5个单词）
            if (words.length >= 5) {
                // 尝试在中间位置分割
                int midPoint = words.length / 2;
                artist = String.join(" ", java.util.Arrays.copyOfRange(words, 0, midPoint));
                track = String.join(" ", java.util.Arrays.copyOfRange(words, midPoint, words.length));
                
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "parseAsLastResort - 基于大写字母分割成功");
                }
                return;
            }
        }
        
        // 尝试基于引号的分割（如"Artist" "Song"）
        if (title.matches(".*\".*\".*\".*\".*")) {
            String[] quotedParts = title.split("\"");
            if (quotedParts.length >= 5) {
                artist = quotedParts[1].trim();
                track = quotedParts[3].trim();
                
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "parseAsLastResort - 基于引号分割成功");
                }
                return;
            }
        }
        
        // 如果所有分割都失败，检查标题长度
        if (title.length() > 60) {
            // 对于长标题，尝试在中间位置分割
            int midPoint = title.length() / 2;
            // 尝试在最近的空格处分割
            int splitPoint = title.lastIndexOf(' ', midPoint);
            if (splitPoint > 0) {
                artist = title.substring(0, splitPoint).trim();
                track = title.substring(splitPoint + 1).trim();
                
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "parseAsLastResort - 长标题在中间位置分割成功");
                }
                return;
            }
        }
        
        // 如果所有分割都失败，整个标题作为艺术家名
        artist = title;
        track = "";
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "parseAsLastResort - 所有分割失败，整个标题作为艺术家名");
        }
    }
    
    /**
     * 清理解析结果
     */
    private void cleanParsingResults() {
        if (artist != null) {
            artist = artist.trim();
            // 移除可能的引号
            if (artist.startsWith("\"") && artist.endsWith("\"")) {
                artist = artist.substring(1, artist.length() - 1);
            }
            // 移除可能的括号内容（如[Live], [Remix]等），但保留可能的歌名部分
            // 只移除方括号内容，因为圆括号内容可能是歌名的一部分
            artist = artist.replaceAll("\\s*\\[.*?\\]\\s*$", "");
            // 对于圆括号，只移除常见的元数据标记，保留其他内容
            artist = artist.replaceAll("\\s*\\((Live|Remix|Edit|Version|Cover|Acoustic|Demo|Instrumental|Explicit)\\)\\s*$", "");
            artist = artist.replaceAll("\\s*\\(\\d{4}\\)\\s*$", ""); // 移除年份
            // 移除多余的空白
            artist = artist.replaceAll("\\s+", " ");
            
            // 移除常见的无用前缀和后缀
            artist = artist.replaceAll("^(Artist:|artist:|Artist |artist )", "");
            artist = artist.replaceAll("\\s*[-:–—]\\s*(Live|Remix|Edit|Version|Cover)\\s*$", "");
            // 移除末尾的连字符、冒号等分隔符（包括多行情况）
            artist = artist.replaceAll("[\\s\\r\\n]*[-:–—][\\s\\r\\n]*$", "");
            
            // 处理全大写或全小写的情况
            if (artist.equals(artist.toUpperCase()) && artist.length() > 3) {
                // 如果全大写且长度大于3，可能是标题格式，转换为标题格式
                artist = artist.substring(0, 1).toUpperCase() + artist.substring(1).toLowerCase();
            }
        }
        
        if (track != null) {
            track = track.trim();
            // 移除可能的引号
            if (track.startsWith("\"") && track.endsWith("\"")) {
                track = track.substring(1, track.length() - 1);
            }
            // 移除可能的括号内容（如[Live], [Remix]等），但保留可能的歌名部分
            // 只移除方括号内容，因为圆括号内容可能是歌名的一部分
            track = track.replaceAll("\\s*\\[.*?\\]\\s*$", "");
            // 对于圆括号，只移除常见的元数据标记，保留其他内容
            track = track.replaceAll("\\s*\\((Live|Remix|Edit|Version|Cover|Acoustic|Demo|Instrumental|Explicit|Official|Audio)\\)\\s*$", "");
            track = track.replaceAll("\\s*\\(\\d{4}\\)\\s*$", ""); // 移除年份
            // 移除多余的空白
            track = track.replaceAll("\\s+", " ");
            
            // 移除常见的无用前缀和后缀
            track = track.replaceAll("^(Title:|title:|Track:|track:|Song:|song:|Title |title |Track |track |Song |song )", "");
            track = track.replaceAll("\\s*[-:–—]\\s*(Live|Remix|Edit|Version|Cover|Official|Audio)\\s*$", "");
            
            // 处理包含年份的情况（如"Song Title (2023)"）
            track = track.replaceAll("\\s*\\(\\d{4}\\)\\s*$", "");
            
            // 处理全大写或全小写的情况
            if (track.equals(track.toUpperCase()) && track.length() > 3) {
                // 如果全大写且长度大于3，可能是标题格式，转换为标题格式
                track = track.substring(0, 1).toUpperCase() + track.substring(1).toLowerCase();
            }
        }
        
        // 处理空值情况
        if (artist != null && artist.isEmpty()) {
            artist = "未知艺术家";
        }
        
        if (track != null && track.isEmpty()) {
            track = "未知歌曲";
        }
        
        // 处理解析结果过于相似的情况（可能是分割错误）
        if (artist != null && track != null && !artist.isEmpty() && !track.isEmpty() && 
            artist.equals(track)) {
            // 如果艺术家和歌曲名相同，可能是分割错误，将整个内容作为歌曲名
            track = artist;
            artist = "未知艺术家";
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "cleanParsingResults - 检测到艺术家和歌曲名相同，调整为未知艺术家");
            }
        }
        
        // 处理艺术家名包含"feat."的情况
        if (artist != null && artist.contains("feat.")) {
            String[] parts = artist.split("feat\\.", 2);
            artist = parts[0].trim();
            if (track != null && !track.isEmpty()) {
                track += " (feat. " + parts[1].trim() + ")";
            }
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "cleanParsingResults - 处理艺术家名包含feat.的情况");
            }
        }
    }
    
    /**
     * 处理包含多个分隔符的情况
     */
    private boolean parseMultipleSeparators(String title) {
        // 尝试找到最合理的分割点
        String[] parts = title.split("\\s*-\\s*", 3);
        
        if (parts.length >= 3) {
            // 如果有三个部分，可能是"艺术家 - 专辑 - 歌曲"格式
            artist = parts[0].trim();
            track = parts[2].trim(); // 使用第三部分作为歌曲名
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseMultipleSeparators - 使用三段式分割: " + artist + " - " + track);
            }
            return true;
        } else if (parts.length >= 2) {
            // 如果只有两个部分，使用标准分割
            artist = parts[0].trim();
            track = parts[1].trim();
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseMultipleSeparators - 使用两段式分割: " + artist + " - " + track);
            }
            return true;
        }
        
        return false;
    }
    
    /**
     * 处理包含HTML实体或特殊字符的情况
     */
    private boolean parseWithHtmlEntities(String title) {
        // 解码HTML实体
        String decodedTitle = title
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&apos;", "'");
        
        // 尝试解析解码后的标题
        if (parseStandardFormats(decodedTitle)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseWithHtmlEntities - HTML实体解码后成功解析");
            }
            return true;
        }
        
        // 如果解码后仍然无法解析，使用解码后的标题作为歌曲名
        artist = "未知艺术家";
        track = decodedTitle.trim();
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "parseWithHtmlEntities - HTML实体解码后使用整个标题作为歌曲名");
        }
        return true;
    }
    
    /**
     * 处理包含过多空白字符的情况
     */
    private boolean parseWithExcessiveWhitespace(String title) {
        // 标准化空白字符
        String normalizedTitle = title.replaceAll("\\s{3,}", " - ");
        
        // 尝试解析标准化后的标题
        if (parseStandardFormats(normalizedTitle)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseWithExcessiveWhitespace - 标准化空白字符后成功解析");
            }
            return true;
        }
        
        // 如果标准化后仍然无法解析，进一步处理
        normalizedTitle = normalizedTitle.replaceAll("\\s+", " ").trim();
        
        // 尝试再次解析
        if (parseStandardFormats(normalizedTitle)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseWithExcessiveWhitespace - 进一步标准化空白字符后成功解析");
            }
            return true;
        }
        
        // 如果仍然无法解析，使用标准化后的标题作为歌曲名
        artist = "未知艺术家";
        track = normalizedTitle;
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "parseWithExcessiveWhitespace - 标准化空白字符后使用整个标题作为歌曲名");
        }
        return true;
    }
    
    /**
     * 处理包含URL或链接的情况
     */
    private boolean parseWithUrl(String title) {
        // 移除URL和链接
        String withoutUrl = title.replaceAll("https?://[^\\s]+", "");
        withoutUrl = withoutUrl.replaceAll("www\\.[^\\s]+", "");
        
        // 清理多余的空白字符
        withoutUrl = withoutUrl.replaceAll("\\s+", " ").trim();
        
        // 尝试解析移除URL后的标题
        if (parseStandardFormats(withoutUrl)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseWithUrl - 移除URL后成功解析");
            }
            return true;
        }
        
        // 如果移除URL后仍然无法解析，使用移除URL后的标题作为歌曲名
        artist = "未知艺术家";
        track = withoutUrl;
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "parseWithUrl - 移除URL后使用整个标题作为歌曲名");
        }
        return true;
    }
    
    /**
     * 处理包含控制字符的情况
     */
    private boolean parseWithControlCharacters(String title) {
        // 移除控制字符
        String cleanTitle = title.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        
        // 尝试解析清理后的标题
        if (parseStandardFormats(cleanTitle)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseWithControlCharacters - 移除控制字符后成功解析");
            }
            return true;
        }
        
        // 如果清理后仍然无法解析，使用清理后的标题作为歌曲名
        artist = "未知艺术家";
        track = cleanTitle;
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "parseWithControlCharacters - 移除控制字符后使用整个标题作为歌曲名");
        }
        return true;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(title);
        parcel.writeString(artist);
        parcel.writeString(track);
        parcel.writeMap(rawMetadata);
    }
}
