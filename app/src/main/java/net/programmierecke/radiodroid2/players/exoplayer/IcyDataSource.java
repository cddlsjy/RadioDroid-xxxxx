package net.programmierecke.radiodroid2.players.exoplayer;


import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

import net.programmierecke.radiodroid2.BuildConfig;
import net.programmierecke.radiodroid2.station.live.ShoutcastInfo;
import net.programmierecke.radiodroid2.station.live.StreamLiveInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static net.programmierecke.radiodroid2.Utils.getMimeType;
import static okhttp3.internal.Util.closeQuietly;

/**
 * An {@link HttpDataSource} that uses {@link OkHttpClient},
 * retrieves stream's {@link ShoutcastInfo} and {@link StreamLiveInfo} if any,
 * attempts to reconnect if connection is lost. These distinguishes it from {@link DefaultHttpDataSource}.
 * <p>
 * When connection is lost attempts to reconnect will made alongside with calling
 * {@link IcyDataSourceListener#onDataSourceConnectionLost()}.
 * After reconnecting time has passed
 * {@link IcyDataSourceListener#onDataSourceConnectionLostIrrecoverably()} will be called.
 **/
public class IcyDataSource implements HttpDataSource {

    public static final long DEFAULT_TIME_UNTIL_STOP_RECONNECTING = 2 * 60 * 1000; // 2 minutes

    public static final long DEFAULT_DELAY_BETWEEN_RECONNECTIONS = 0;

    public interface IcyDataSourceListener {
        /**
         * Called on first connection and after successful reconnection.
         */
        void onDataSourceConnected();

        /**
         * Called when connection is lost and reconnection attempts will be made.
         */
        void onDataSourceConnectionLost();

        /**
         * Called when data source gives up reconnecting.
         */
        void onDataSourceConnectionLostIrrecoverably();

        void onDataSourceShoutcastInfo(@Nullable ShoutcastInfo shoutcastInfo);

        void onDataSourceStreamLiveInfo(StreamLiveInfo streamLiveInfo);

        void onDataSourceBytesRead(byte[] buffer, int offset, int length);
    }

    private static final String TAG = "IcyDataSource";

    private DataSpec dataSpec;

    private final OkHttpClient httpClient;
    private final TransferListener transferListener;
    private final IcyDataSourceListener dataSourceListener;

    private Request request;

    private ResponseBody responseBody;
    private Map<String, List<String>> responseHeaders;

    int metadataBytesToSkip = 0;
    int remainingUntilMetadata = Integer.MAX_VALUE;
    private boolean opened;

    ShoutcastInfo shoutcastInfo;
    private StreamLiveInfo streamLiveInfo;

    public IcyDataSource(@NonNull OkHttpClient httpClient,
                         @NonNull TransferListener listener,
                         @NonNull IcyDataSourceListener dataSourceListener) {
        this.httpClient = httpClient;
        this.transferListener = listener;
        this.dataSourceListener = dataSourceListener;
    }

    @Override
    public long open(DataSpec dataSpec) throws HttpDataSourceException {
        close();

        this.dataSpec = dataSpec;

        final boolean allowGzip = (dataSpec.flags & DataSpec.FLAG_ALLOW_GZIP) != 0;

        HttpUrl url = HttpUrl.parse(dataSpec.uri.toString());
        Request.Builder builder = new Request.Builder().url(url)
                .addHeader("Icy-MetaData", "1");

        if (!allowGzip) {
            builder.addHeader("Accept-Encoding", "identity");
        }

        request = builder.build();

        return connect();
    }

    private long connect() throws HttpDataSourceException {
        Response response;
        try {
            response = httpClient.newCall(request).execute();
        } catch (IOException e) {
            throw new HttpDataSourceException("Unable to connect to " + dataSpec.uri.toString(), e,
                    dataSpec, HttpDataSourceException.TYPE_OPEN);
        }

        final int responseCode = response.code();

        if (!response.isSuccessful()) {
            final Map<String, List<String>> headers = request.headers().toMultimap();
            throw new InvalidResponseCodeException(responseCode, headers, dataSpec);
        }

        responseBody = response.body();
        assert responseBody != null;

        responseHeaders = response.headers().toMultimap();

        final MediaType contentType = responseBody.contentType();

        final String type = contentType == null ? getMimeType(dataSpec.uri.toString(), "audio/mpeg") : contentType.toString().toLowerCase();

        if (!REJECT_PAYWALL_TYPES.apply(type)) {
            close();
            throw new InvalidContentTypeException(type, dataSpec);
        }

        opened = true;

        dataSourceListener.onDataSourceConnected();
        transferListener.onTransferStart(this, dataSpec, true);

        if (type.equals("application/vnd.apple.mpegurl") || type.equals("application/x-mpegurl")) {
            return responseBody.contentLength();
        } else {
            // try to get shoutcast information from stream connection
            shoutcastInfo = ShoutcastInfo.Decode(response);
            dataSourceListener.onDataSourceShoutcastInfo(shoutcastInfo);

            metadataBytesToSkip = 0;
            if (shoutcastInfo != null) {
                remainingUntilMetadata = shoutcastInfo.metadataOffset;
            } else {
                remainingUntilMetadata = Integer.MAX_VALUE;
            }

            return responseBody.contentLength();
        }
    }

    @Override
    public void close() throws HttpDataSourceException {
        if (opened) {
            opened = false;
            transferListener.onTransferEnd(this, dataSpec, true);
        }

        if (responseBody != null) {
            closeQuietly(responseBody);
            responseBody = null;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
        try {
            final int bytesTransferred = readInternal(buffer, offset, readLength);
            transferListener.onBytesTransferred(this, dataSpec, true, bytesTransferred);
            return bytesTransferred;
        } catch (HttpDataSourceException readError) {
            dataSourceListener.onDataSourceConnectionLost();
            throw readError;
        }
    }

    void sendToDataSourceListenersWithoutMetadata(byte[] buffer, int offset, int bytesAvailable) {
        int canSkip = Math.min(metadataBytesToSkip, bytesAvailable);
        offset += canSkip;
        bytesAvailable -= canSkip;
        remainingUntilMetadata -= canSkip;
        while (bytesAvailable > 0) {
            if (bytesAvailable > remainingUntilMetadata) { // do we need to handle a metadata frame at all?
                if (remainingUntilMetadata > 0) { // is there any audio data before the metadata frame?
                    dataSourceListener.onDataSourceBytesRead(buffer, offset, remainingUntilMetadata);
                    offset += remainingUntilMetadata;
                    bytesAvailable -= remainingUntilMetadata;
                }
                
                // 读取metadata块的大小（第一个字节表示metadata块的大小，单位是16字节）
                int metadataSizeByte = buffer[offset] & 0xFF;
                metadataBytesToSkip = metadataSizeByte * 16 + 1; // +1 for the size byte itself
                
                // 如果metadata块大小大于0，则处理metadata块
                if (metadataSizeByte > 0) {
                    // 确保我们有足够的字节来读取完整的metadata块
                    if (bytesAvailable >= metadataBytesToSkip) {
                        // 提取metadata块（跳过大小字节）
                        byte[] metadataBytes = new byte[metadataSizeByte * 16];
                        System.arraycopy(buffer, offset + 1, metadataBytes, 0, metadataSizeByte * 16);
                        
                        // 处理metadata块
                        processMetadataBlock(metadataBytes);
                    } else {
                        // 如果没有足够的字节，记录警告并跳过
                        Log.w(TAG, "metadata块不完整，需要" + metadataBytesToSkip + "字节，但只有" + bytesAvailable + "字节可用");
                        
                        // 尝试读取可用的部分
                        if (bytesAvailable > 1) { // 至少有大小字节+一些数据
                            int availableDataSize = bytesAvailable - 1; // 减去大小字节
                            byte[] partialMetadataBytes = new byte[availableDataSize];
                            System.arraycopy(buffer, offset + 1, partialMetadataBytes, 0, availableDataSize);
                            
                            // 处理部分metadata块
                            processMetadataBlock(partialMetadataBytes);
                        }
                    }
                }
                
                remainingUntilMetadata = shoutcastInfo.metadataOffset + metadataBytesToSkip;
            }

            int bytesLeft = Math.min(bytesAvailable, remainingUntilMetadata);
            if (bytesLeft > metadataBytesToSkip) { // is there audio data left we need to send?
                dataSourceListener.onDataSourceBytesRead(buffer, offset + metadataBytesToSkip, bytesLeft - metadataBytesToSkip);
                metadataBytesToSkip = 0;
            } else {
                metadataBytesToSkip -= bytesLeft;
            }
            offset += bytesLeft;
            bytesAvailable -= bytesLeft;
            remainingUntilMetadata -= bytesLeft;
        }
    }

    /**
     * 处理metadata块，提取并解析其中的信息
     * @param metadataBytes metadata块的原始字节数据
     */
    private void processMetadataBlock(byte[] metadataBytes) {
        if (metadataBytes == null || metadataBytes.length == 0) {
            return;
        }
        
        // 记录原始metadata字节数据
        if (BuildConfig.DEBUG) {
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(metadataBytes.length, 100); i++) {
                hexString.append(String.format("%02X ", metadataBytes[i]));
            }
            Log.d(TAG, "processMetadataBlock - 原始metadata字节数据（前100字节）: " + hexString.toString());
            Log.d(TAG, "processMetadataBlock - metadata块长度: " + metadataBytes.length);
        }
        
        // 处理metadata块，移除末尾的null字节
        int actualLength = metadataBytes.length;
        while (actualLength > 0 && metadataBytes[actualLength - 1] == 0) {
            actualLength--;
        }
        
        if (actualLength == 0) {
            Log.d(TAG, "processMetadataBlock - metadata块为空或全是null字节");
            return;
        }
        
        // 将metadata块转换为字符串
        String metadataString;
        try {
            // 尝试使用ISO-8859-1编码，这是ICY元数据的标准编码
            metadataString = new String(metadataBytes, 0, actualLength, "ISO-8859-1");
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "processMetadataBlock - metadata字符串: " + metadataString);
                Log.d(TAG, "processMetadataBlock - metadata字符串长度: " + metadataString.length());
            }
            
            // 解析metadata字符串，提取StreamTitle等字段
            Map<String, String> metadataMap = parseMetadataString(metadataString);
            
            if (metadataMap.containsKey("StreamTitle")) {
                String streamTitle = metadataMap.get("StreamTitle");
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "processMetadataBlock - 提取的StreamTitle: " + streamTitle);
                }
                
                // 创建StreamLiveInfo对象并发送给监听器
                StreamLiveInfo streamLiveInfo = new StreamLiveInfo(metadataMap);
                dataSourceListener.onDataSourceStreamLiveInfo(streamLiveInfo);
            }
        } catch (Exception e) {
            Log.e(TAG, "processMetadataBlock - 处理metadata块时出错: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解析metadata字符串，提取键值对
     * @param metadataString metadata字符串
     * @return 包含所有字段的Map
     */
    private Map<String, String> parseMetadataString(String metadataString) {
        Map<String, String> metadataMap = new java.util.HashMap<>();
        
        if (metadataString == null || metadataString.isEmpty()) {
            return metadataMap;
        }
        
        // metadata字符串通常包含多个键值对，用分号分隔
        // 例如：StreamTitle='Artist - Song';StreamURL=''
        String[] pairs = metadataString.split(";");
        
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            
            // 每个键值对的格式通常是 key='value'
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex == -1) {
                continue;
            }
            
            String key = pair.substring(0, equalsIndex).trim();
            String value = pair.substring(equalsIndex + 1).trim();
            
            // 移除值两端的引号（如果有）
            if (value.length() >= 2 && 
                ((value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') ||
                 (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"'))) {
                value = value.substring(1, value.length() - 1);
            }
            
            metadataMap.put(key, value);
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "parseMetadataString - 解析键值对: " + key + " = " + value);
            }
        }
        
        return metadataMap;
    }

    private int readInternal(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
        if (responseBody == null) {
            throw new HttpDataSourceException(dataSpec, HttpDataSourceException.TYPE_READ);
        }

        InputStream stream = responseBody.byteStream();

        int bytesRead = 0;
        try {
            bytesRead = stream.read(buffer, offset, readLength);
        } catch (IOException e) {
            throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_READ);
        }

        sendToDataSourceListenersWithoutMetadata(buffer, offset, bytesRead);

        return bytesRead;
    }

    @Override
    public Uri getUri() {
        return dataSpec.uri;
    }

    @Override
    public void setRequestProperty(String name, String value) {
        // Ignored
    }

    @Override
    public void clearRequestProperty(String name) {
        // Ignored
    }

    @Override
    public void clearAllRequestProperties() {
        // Ignored
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return responseHeaders;
    }

    @Override
    public int getResponseCode() {
        return 0;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {

    }
}
