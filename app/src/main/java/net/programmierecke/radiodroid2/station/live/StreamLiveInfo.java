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
        Log.i(TAG, "StreamLiveInfo构造函数 - 原始元数据: " + rawMetadata);

        if (rawMetadata != null && rawMetadata.containsKey("StreamTitle")) {
            title = rawMetadata.get("StreamTitle");

            Log.i(TAG, "StreamLiveInfo构造函数 - StreamTitle: " + title);
            Log.i(TAG, "StreamLiveInfo构造函数 - StreamTitle长度: " + (title != null ? title.length() : 0));
            
            // 检查标题中是否包含问号
            if (title != null && title.contains("?")) {
                Log.w(TAG, "StreamLiveInfo构造函数 - StreamTitle中包含问号: " + title);
                
                // 打印问号字符的详细信息
                StringBuilder questionMarks = new StringBuilder();
                for (int i = 0; i < title.length(); i++) {
                    char c = title.charAt(i);
                    if (c == '?') {
                        questionMarks.append(String.format("位置%d ", i));
                    }
                }
                Log.w(TAG, "StreamLiveInfo构造函数 - StreamTitle问号位置: " + questionMarks.toString());
            }
            
            // 打印标题中每个字符的Unicode值
            if (title != null && !title.isEmpty()) {
                StringBuilder charCodes = new StringBuilder();
                for (int i = 0; i < Math.min(title.length(), 50); i++) {
                    char c = title.charAt(i);
                    charCodes.append(String.format("'%c'(%04X) ", c, (int) c));
                }
                Log.i(TAG, "StreamLiveInfo构造函数 - StreamTitle的前50个字符的Unicode值: " + charCodes.toString());
            }

            if (!TextUtils.isEmpty(title)) {
                // 尝试智能解析元数据
                parseMetadataIntelligently(title);

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "StreamLiveInfo构造函数 - 解析后艺术家: " + artist);
                    Log.d(TAG, "StreamLiveInfo构造函数 - 解析后曲目: " + track);
                    
                    // 检查艺术家和曲目中是否包含问号
                    if (artist != null && artist.contains("?")) {
                        Log.w(TAG, "StreamLiveInfo构造函数 - 艺术家中包含问号: " + artist);
                    }
                    if (track != null && track.contains("?")) {
                        Log.w(TAG, "StreamLiveInfo构造函数 - 曲目中包含问号: " + track);
                    }
                    
                    // 打印艺术家和曲目中每个字符的Unicode值
                    if (artist != null && !artist.isEmpty()) {
                        StringBuilder artistCharCodes = new StringBuilder();
                        for (int i = 0; i < Math.min(artist.length(), 30); i++) {
                            char c = artist.charAt(i);
                            artistCharCodes.append(String.format("'%c'(%04X) ", c, (int) c));
                        }
                        Log.d(TAG, "StreamLiveInfo构造函数 - 艺术家的前30个字符的Unicode值: " + artistCharCodes.toString());
                    }
                    
                    if (track != null && !track.isEmpty()) {
                        StringBuilder trackCharCodes = new StringBuilder();
                        for (int i = 0; i < Math.min(track.length(), 30); i++) {
                            char c = track.charAt(i);
                            trackCharCodes.append(String.format("'%c'(%04X) ", c, (int) c));
                        }
                        Log.d(TAG, "StreamLiveInfo构造函数 - 曲目的前30个字符的Unicode值: " + trackCharCodes.toString());
                    }
                }
            } else if (title != null && title.isEmpty()) {
                // 处理空字符串情况（可能是检测到数据损坏）
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "StreamLiveInfo构造函数 - 检测到空标题，可能是数据损坏");
                }
                // 保持artist和track为空字符串，不进行解析
            }
        }
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
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "parseMetadataIntelligently - 原始标题: " + title);
        }
        
        // 默认值
        artist = "";
        track = "";
        
        // 预处理标题
        title = preprocessTitle(title);
        if (title.isEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseMetadataIntelligently - 预处理后标题为空，返回");
            }
            return;
        }
        
        // 检查是否包含过多问号，如果是则忽略此元数据
        if (isMetadataCorrupted(title)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseMetadataIntelligently - 检测到元数据损坏，忽略此元数据");
            }
            return;
        }
        
        // 第一阶段：处理标准格式和常见变体
        if (parseStandardFormats(title)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseMetadataIntelligently - 标准格式解析成功 - 艺术家: '" + artist + "', 歌曲: '" + track + "'");
            }
            // 清理解析结果
            cleanParsingResults();
            return;
        }
        
        // 第二阶段：处理特殊格式和极端情况
        if (parseSpecialFormats(title)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseMetadataIntelligently - 特殊格式解析成功 - 艺术家: '" + artist + "', 歌曲: '" + track + "'");
            }
            // 清理解析结果
            cleanParsingResults();
            return;
        }
        
        // 第三阶段：处理字段格式
        if (parseFieldFormats(title)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseMetadataIntelligently - 字段格式解析成功 - 艺术家: '" + artist + "', 歌曲: '" + track + "'");
            }
            // 清理解析结果
            cleanParsingResults();
            return;
        }
        
        // 第四阶段：作为最后手段，尝试智能分割
        parseAsLastResort(title);
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "parseMetadataIntelligently - 最后手段解析完成 - 艺术家: '" + artist + "', 歌曲: '" + track + "'");
        }
        
        // 清理结果
        cleanParsingResults();
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "parseMetadataIntelligently - 最终解析结果 - 艺术家: '" + artist + "', 歌曲: '" + track + "'");
        }
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
        // 构建字段模式，如title="
        String fieldPattern = fieldName + "=\"";
        int fieldIndex = input.indexOf(fieldPattern);
        if (fieldIndex == -1) {
            return "";
        }
        
        // 跳过字段名和等号和引号
        int valueStart = fieldIndex + fieldPattern.length();
        
        // 找到匹配的结束引号
        int valueEnd = input.indexOf('"', valueStart);
        if (valueEnd != -1) {
            return input.substring(valueStart, valueEnd);
        }
        
        // 如果没有找到结束引号，返回空字符串
        return "";
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
        // 标准格式 "艺术家 - 歌曲名"
        if (title.contains(" - ")) {
            String[] parts = title.split(" - ", 2);
            artist = parts[0].trim();
            track = parts[1].trim();
            
            // 检查track部分是否包含text字段
            if (track.contains("text=")) {
                track = extractTextValue(track);
            }
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseStandardFormats - 使用标准格式解析");
            }
            return true;
        }
        
        // 包含"+"分隔符的格式（处理一些特殊格式）
        if (title.contains("+") && !title.contains("text=") && !title.contains("title=")) {
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
        // 包含text字段的格式
        if (title.contains("text=")) {
            return parseTextFieldFormat(title);
        }
        
        // 包含title和artist字段的格式
        if (title.contains("title=") && title.contains("artist=")) {
            artist = extractFieldValue(title, "artist");
            track = extractFieldValue(title, "title");
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseFieldFormats - 使用title/artist字段格式解析");
            }
            return true;
        }
        
        return false;
    }
    
    /**
     * 解析包含text字段的格式
     */
    private boolean parseTextFieldFormat(String title) {
        // 尝试从text字段前提取艺术家
        int textIndex = title.indexOf("text=");
        if (textIndex > 0) {
            // 查找text字段前的艺术家名称
            String beforeText = title.substring(0, textIndex).trim();
            if (beforeText.endsWith(" - ")) {
                artist = beforeText.substring(0, beforeText.length() - 3).trim();
            } else {
                artist = beforeText;
            }
        }
        track = extractTextValue(title);
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "parseFieldFormats - 使用text字段格式解析");
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
            if (words.length >= 4) {
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
        
        // 如果所有分割都失败，整个标题作为歌曲名
        artist = "";
        track = title;
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "parseAsLastResort - 所有分割失败，整个标题作为歌曲名");
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
