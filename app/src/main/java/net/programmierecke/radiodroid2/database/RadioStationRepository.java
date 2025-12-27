package net.programmierecke.radiodroid2.database;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import java.io.File;

import androidx.lifecycle.LiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.paging.DataSource;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;

import net.programmierecke.radiodroid2.RadioBrowserServerManager;
import net.programmierecke.radiodroid2.RadioDroidApp;
import net.programmierecke.radiodroid2.Utils;
import net.programmierecke.radiodroid2.service.DatabaseUpdateWorker;
import net.programmierecke.radiodroid2.station.DataRadioStation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

public class RadioStationRepository {
    private static final String TAG = "RadioStationRepository";
    
    private RadioStationDao radioStationDao;
    private RadioStationDao tempRadioStationDao; // 临时数据库的DAO
    private UpdateTimestampDao updateTimestampDao;
    private Context context;
    private Executor executor = Executors.newSingleThreadExecutor();
    
    // 静态锁对象，确保同步方法不会被多个线程同时调用
    private static final Object sSyncLock = new Object();
    
    // 单例模式
    private static volatile RadioStationRepository INSTANCE;
    
    public static RadioStationRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (RadioStationRepository.class) {
                if (INSTANCE == null) {
                    RadioDroidDatabase db = RadioDroidDatabase.getDatabase(context);
                    INSTANCE = new RadioStationRepository(db.radioStationDao(), context);
                }
            }
        }
        return INSTANCE;
    }
    
    private RadioStationRepository(RadioStationDao radioStationDao, Context context) {
        this.radioStationDao = radioStationDao;
        // 创建临时数据库的实例
        RadioDroidDatabase tempDatabase = Room.databaseBuilder(context.getApplicationContext(),
                RadioDroidDatabase.class, "radio_droid_database_temp")
                .addMigrations(RadioDroidDatabase.MIGRATION_3_4)
                .build();
        this.tempRadioStationDao = tempDatabase.radioStationDao();
        // 获取UpdateTimestampDao
        RadioDroidDatabase db = RadioDroidDatabase.getDatabase(context);
        this.updateTimestampDao = db.updateTimestampDao();
        this.context = context;
    }
    
    // 从网络获取并存储所有电台数据
    public void syncAllStationsFromNetwork(Context context, SyncCallback callback) {
        executor.execute(() -> {
            syncAllStationsFromNetworkInternal(context, callback);
        });
    }
    
    /**
     * 清空临时数据库
     * 用于取消更新时清除临时数据，防止恢复时从上次进度继续
     */
    public void clearTempDatabase() {
        synchronized (sSyncLock) {
            try {
                if (tempRadioStationDao != null) {
                    tempRadioStationDao.deleteAll();
                    Log.d(TAG, "Successfully cleared temporary database");
                } else {
                    Log.w(TAG, "tempRadioStationDao is null, cannot clear temporary database");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error clearing temporary database", e);
            }
        }
    }
    
    /**
     * 获取临时数据库中的电台数量
     */
    public int getTempDatabaseCount() {
        synchronized (sSyncLock) {
            try {
                if (tempRadioStationDao != null) {
                    return tempRadioStationDao.getCount();
                } else {
                    Log.w(TAG, "tempRadioStationDao is null, returning 0");
                    return 0;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting temp database count", e);
                return 0;
            }
        }
    }
    
    // 内部同步方法，不使用Executor，直接在当前线程执行
    public void syncAllStationsFromNetworkInternal(Context context, SyncCallback callback) {
        syncAllStationsFromNetworkInternal(context, callback, false);
    }
    
    // 内部同步方法，支持恢复模式
    public void syncAllStationsFromNetworkInternal(Context context, SyncCallback callback, boolean resumeMode) {
        // 添加线程信息日志
        Log.d(TAG, "Starting syncAllStationsFromNetworkInternal on thread: " + Thread.currentThread().getId() + ", name: " + Thread.currentThread().getName());
        
        // 使用同步块确保只有一个同步操作在运行
        synchronized (sSyncLock) {
            Log.d(TAG, "Acquired sync lock, starting station synchronization");
            try {
                Log.d(TAG, "开始同步电台数据");
                callback.onProgress("正在检查网络连接", 0, 100);
            
            // 检查网络检查结果
            RadioBrowserServerManager.ServerInfo fastestServer = checkNetworkAndGetFastestServer(context, callback);
            if (fastestServer == null) {
                Log.e(TAG, "网络检查失败，无法获取服务器信息");
                callback.onError("网络检查失败，无法获取服务器信息");
                throw new RuntimeException("网络检查失败，无法获取服务器信息");
            }
            
            RadioDroidApp radioDroidApp = (RadioDroidApp) context.getApplicationContext();
            OkHttpClient httpClient = radioDroidApp.getHttpClient();
            
            // 设置当前使用的服务器
            RadioBrowserServerManager.setCurrentServer(fastestServer.server);
            
            callback.onProgress("正在获取电台总数", 0, 100);
        // 首先获取电台总数
        Log.d(TAG, "Attempting to download stats from: " + fastestServer.server + "/json/stats");
        String statsResult = Utils.downloadFeedFromServer(httpClient, radioDroidApp, fastestServer.server, "json/stats", fastestServer.useHttps, true, null);
        Log.d(TAG, "Download completed, result is " + (statsResult == null ? "null" : "non-null"));
        if (statsResult == null) {
            Log.e(TAG, "获取服务器统计信息失败，服务器返回空结果");
            callback.onError("获取服务器统计信息失败");
            // 不重置更新状态，让错误处理流程自然进行
            throw new RuntimeException("获取服务器统计信息失败");
        }
            
            // 解析统计信息获取电台总数
            int totalStations = 0;
            try {
                Log.d(TAG, "服务器返回的原始统计信息: " + statsResult);
                org.json.JSONObject stats = new org.json.JSONObject(statsResult);
                totalStations = stats.getInt("stations");
                Log.d(TAG, "解析得到的电台总数: " + totalStations);
                callback.onProgress("查询到网络数据库现存 " + totalStations + " 个电台", 0, totalStations);
            } catch (Exception e) {
                Log.e(TAG, "解析统计信息失败", e);
                callback.onError("解析统计信息失败: " + e.getMessage());
                // 不重置更新状态，让错误处理流程自然进行
                throw new RuntimeException("解析统计信息失败: " + e.getMessage());
            }
            
            // 获取主数据库中的电台数量
            int mainDatabaseCount = radioStationDao.getCount();
            Log.d(TAG, "主数据库中的电台数量: " + mainDatabaseCount);
            
            callback.onProgress("开始更新临时数据库", 0, totalStations);
            
            // 只有在非恢复模式下才清空临时数据库
            if (!resumeMode) {
                // 清空临时数据库
                tempRadioStationDao.deleteAll();
                Log.d(TAG, "已清空临时数据库");
            } else {
                // 恢复模式下，检查临时数据库中已有的数据
                int existingTempCount = tempRadioStationDao.getCount();
                Log.d(TAG, "恢复模式：临时数据库中已有 " + existingTempCount + " 个电台");
                if (existingTempCount > 0) {
                    callback.onProgress("恢复下载进度", existingTempCount, totalStations);
                }
            }
            
            // 使用分页获取所有电台数据，每页50个
            final int pageSize = 50; // 每页50个电台
            int totalPages = (int) Math.ceil((double) totalStations / pageSize);
            int totalDownloaded = 0;
            int batchSize = 10; // 批量处理大小
            
            // 在恢复模式下，检查临时数据库中已有的电台数量，并从相应的页面开始下载
            int startPage = 0;
            if (resumeMode) {
                int existingTempCount = tempRadioStationDao.getCount();
                if (existingTempCount > 0) {
                    // 计算应该从哪一页开始下载
                    startPage = existingTempCount / pageSize;
                    totalDownloaded = existingTempCount;
                    Log.d(TAG, "恢复模式：从第 " + (startPage + 1) + " 页开始下载，已有 " + totalDownloaded + " 个电台");
                }
            }
            
            // 用于批量插入的临时列表
            List<RadioStation> batchInsertList = new ArrayList<>(pageSize * batchSize);
            
            for (int page = startPage; page < totalPages; page++) {
                int skip = page * pageSize;
                int currentStationCount = Math.min(skip + pageSize, totalStations);
                
                String urlWithParams = "json/stations?limit=" + pageSize + "&offset=" + skip + "&hidebroken=true";
                
                String resultString = null;
                int retryCount = 0;
                final int maxRetries = 3;
                
                while (retryCount < maxRetries && resultString == null) {
                    if (retryCount > 0) {
                        Log.w(TAG, "第 " + (page + 1) + " 页第 " + retryCount + " 次重试");
                        try {
                            Thread.sleep(1000 * retryCount);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            Log.w(TAG, "重试等待被中断", e);
                            break;
                        }
                    }
                    
                    resultString = Utils.downloadFeedFromServer(httpClient, radioDroidApp, fastestServer.server, urlWithParams, fastestServer.useHttps, true, null);
                    retryCount++;
                }
                
                if (resultString != null) {
                    List<DataRadioStation> dataStations = DataRadioStation.DecodeJson(resultString);
                    
                    if (dataStations != null && !dataStations.isEmpty()) {
                        for (DataRadioStation dataStation : dataStations) {
                            RadioStation radioStation = RadioStation.fromDataRadioStation(dataStation);
                            batchInsertList.add(radioStation);
                        }
                        
                        totalDownloaded += dataStations.size();
                        Log.d(TAG, "已下载 " + totalDownloaded + "/" + totalStations + " 个电台");
                        
                        if ((page + 1) % batchSize == 0 || page == totalPages - 1) {
                            if (!batchInsertList.isEmpty()) {
                                tempRadioStationDao.insertAll(batchInsertList);
                                Log.d(TAG, "批量插入了 " + batchInsertList.size() + " 个电台到临时数据库");
                                batchInsertList.clear();
                                
                                int actualTempCount = tempRadioStationDao.getCount();
                                if (actualTempCount > totalDownloaded) {
                                    Log.w(TAG, "进度不一致：临时数据库有 " + actualTempCount + " 个电台，但统计为 " + totalDownloaded);
                                    totalDownloaded = actualTempCount;
                                }
                            }
                        }
                        
                        callback.onProgress("正在下载电台数据", totalDownloaded, totalStations);
                    } else {
                        Log.w(TAG, "第 " + (page + 1) + " 页数据为空");
                    }
                } else {
                    Log.e(TAG, "第 " + (page + 1) + " 页下载失败，已重试 " + maxRetries + " 次，跳过该页");
                    if (totalStations > 0) {
                        int estimatedProgress = Math.min((page + 1) * pageSize, totalStations);
                        callback.onProgress("网络请求失败，跳过该页继续", estimatedProgress, totalStations);
                    }
                }
                
                if ((page + 1) % batchSize == 0) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Log.w(TAG, "休眠被中断", e);
                        int tempDatabaseCount = tempRadioStationDao.getCount();
                        Log.d(TAG, "用户中断，临时数据库中的电台数量: " + tempDatabaseCount);
                        
                        callback.onSuccess("更新已中断，已下载 " + totalDownloaded + " 个电台并保存到临时数据库");
                        return;
                    }
                }
            }
            
            if (totalDownloaded > 0) {
                Log.d(TAG, "数据下载完成，共同步 " + totalDownloaded + " 个电台");
                
                // 获取临时数据库中的电台数量
                int tempDatabaseCount = tempRadioStationDao.getCount();
                Log.d(TAG, "临时数据库中的电台数量: " + tempDatabaseCount);
                
                // 比较两个数据库的电台数量
                if (tempDatabaseCount >= mainDatabaseCount) {
                    // 临时数据库的电台数量大于或等于主数据库，使用临时数据库
                    Log.d(TAG, "临时数据库的电台数量(" + tempDatabaseCount + ")大于或等于主数据库(" + mainDatabaseCount + ")，将使用临时数据库");
                    
                    // 将主数据库的数据清空，然后将临时数据库的数据复制到主数据库
                    radioStationDao.deleteAll();
                    List<RadioStation> allStationsFromTemp = tempRadioStationDao.getAllStations();
                    if (!allStationsFromTemp.isEmpty()) {
                        radioStationDao.insertAll(allStationsFromTemp);
                        Log.d(TAG, "已将临时数据库的 " + allStationsFromTemp.size() + " 个电台复制到主数据库");
                    }
                    
                    // 清空临时数据库
                    tempRadioStationDao.deleteAll();
                    Log.d(TAG, "已清空临时数据库");
                    
                    // 更新数据库时间戳
                    updateDatabaseTimestamp(context);
                    Log.d(TAG, "已更新数据库时间戳");
                    
                    String completionMessage = "更新完成，共同步 " + totalDownloaded + " 个电台，已切换到新数据";
                    
                    // 发送数据库更新完成广播
                    Intent databaseUpdatedIntent = new Intent("net.programmierecke.radiodroid2.DATABASE_UPDATED");
                    LocalBroadcastManager.getInstance(context).sendBroadcast(databaseUpdatedIntent);
                    Log.d(TAG, "已发送数据库更新完成广播");
                    
                    callback.onSuccess(completionMessage);
                } else {
                    // 临时数据库的电台数量小于主数据库，询问用户是否替换
                    Log.d(TAG, "临时数据库的电台数量(" + tempDatabaseCount + ")小于主数据库(" + mainDatabaseCount + ")，将询问用户是否替换");
                    
                    // 通过回调询问用户是否替换数据
                    boolean shouldReplace = callback.onConfirmReplace(
                        "新数据(" + tempDatabaseCount + "个)比现有数据(" + mainDatabaseCount + "个)少，是否替换？", 
                        tempDatabaseCount, 
                        mainDatabaseCount
                    );
                    
                    if (shouldReplace) {
                        // 用户确认替换，执行替换操作
                        Log.d(TAG, "用户确认替换，将使用临时数据库");
                        
                        // 将主数据库的数据清空，然后将临时数据库的数据复制到主数据库
                        radioStationDao.deleteAll();
                        List<RadioStation> allStationsFromTemp = tempRadioStationDao.getAllStations();
                        if (!allStationsFromTemp.isEmpty()) {
                            radioStationDao.insertAll(allStationsFromTemp);
                            Log.d(TAG, "已将临时数据库的 " + allStationsFromTemp.size() + " 个电台复制到主数据库");
                        }
                        
                        // 更新数据库时间戳
                        updateDatabaseTimestamp(context);
                        Log.d(TAG, "已更新数据库时间戳");
                        
                        String completionMessage = "更新完成，共同步 " + totalDownloaded + " 个电台，已切换到新数据";
                        
                        // 发送数据库更新完成广播
                        Intent databaseUpdatedIntent = new Intent("net.programmierecke.radiodroid2.DATABASE_UPDATED");
                        LocalBroadcastManager.getInstance(context).sendBroadcast(databaseUpdatedIntent);
                        Log.d(TAG, "已发送数据库更新完成广播");
                        
                        callback.onSuccess(completionMessage);
                    } else {
                        // 用户取消替换，继续使用主数据库
                        Log.d(TAG, "用户取消替换，将继续使用主数据库");
                        
                        String completionMessage = "更新完成，但用户选择继续使用现有数据";
                        
                        // 发送数据库更新完成广播
                        Intent databaseUpdatedIntent = new Intent("net.programmierecke.radiodroid2.DATABASE_UPDATED");
                        LocalBroadcastManager.getInstance(context).sendBroadcast(databaseUpdatedIntent);
                        Log.d(TAG, "已发送数据库更新完成广播");
                        
                        callback.onSuccess(completionMessage);
                    }
                    
                    // 清空临时数据库
                    tempRadioStationDao.deleteAll();
                    Log.d(TAG, "已清空临时数据库");
                }
            } else {
                Log.e(TAG, "没有获取到任何电台数据");
                callback.onError("没有获取到任何电台数据");
                // 不重置更新状态，让错误处理流程自然进行
                throw new RuntimeException("没有获取到任何电台数据");
            }
        } catch (RuntimeException e) {
            // 重新抛出RuntimeException
            throw e;
        } catch (Exception e) {
                Log.e(TAG, "同步电台数据时出错", e);
                callback.onError("同步出错: " + e.getMessage());
                // 不重置更新状态，让错误处理流程自然进行
                throw new RuntimeException("同步出错: " + e.getMessage());
            }
        } // 结束synchronized块
    }
    
    // 检查网络并获取最快的服务器
    private RadioBrowserServerManager.ServerInfo checkNetworkAndGetFastestServer(Context context, SyncCallback callback) {
        try {
            // 检查网络连接状态
            if (!isNetworkAvailable(context)) {
                Log.e(TAG, "网络连接不可用");
                callback.onError("网络连接不可用，请检查网络设置");
                throw new RuntimeException("网络连接不可用，请检查网络设置");
            }
            
            // 检查电量状态
            checkBatteryLevel(context, callback);
            
            // 检查存储空间
            checkStorageSpace(context, callback);
            
            // 不再使用缓存结果，每次都执行新的网络检查
            Log.d(TAG, "执行新的网络检查");
            callback.onProgress("正在检查网络连接速度", 0, 100);
            
            // 测试所有连接速度
            Map<String, Long> results = RadioBrowserServerManager.testAllConnectionSpeeds(context);
            
            // 保存结果供参考
            SharedPreferences sharedPref = context.getSharedPreferences("NetworkCheckResults", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            for (Map.Entry<String, Long> entry : results.entrySet()) {
                editor.putLong(entry.getKey(), entry.getValue());
            }
            editor.putLong("timestamp", System.currentTimeMillis());
            editor.apply();
            
            // 返回最快的服务器
            return findFastestServerFromResults(results);
        } catch (Exception e) {
            Log.e(TAG, "检查网络连接时出错", e);
            callback.onError("检查网络连接出错: " + e.getMessage());
            // 不重置更新状态，让错误处理流程自然进行
            throw new RuntimeException("检查网络连接出错: " + e.getMessage());
        }
    }
    
    // 从测试结果中找到最快的服务器
    private RadioBrowserServerManager.ServerInfo findFastestServerFromResults(Map<String, Long> results) {
        long minTime = Long.MAX_VALUE;
        String fastestServer = null;
        boolean useHttps = false;
        
        for (Map.Entry<String, Long> entry : results.entrySet()) {
            if (entry.getValue() < minTime) {
                minTime = entry.getValue();
                String[] parts = entry.getKey().split("_");
                fastestServer = parts[0];
                useHttps = "HTTPS".equals(parts[1]);
            }
        }
        
        if (fastestServer != null) {
            Log.d(TAG, "找到最快的服务器: " + fastestServer + " (HTTPS: " + useHttps + ")");
            return new RadioBrowserServerManager.ServerInfo(fastestServer, useHttps);
        }
        
        Log.e(TAG, "没有找到可用的服务器");
        return null;
    }
    
    // 获取本地数据库中的电台数量
    public void getStationCount(StationCountCallback callback) {
        executor.execute(() -> {
            try {
                int count = radioStationDao.getCount();
                if (callback != null) {
                    callback.onStationCountReceived(count);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting station count", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
    
    // 同步获取电台数量（仅在后台线程中使用）
    public int getStationCountSync() {
        return radioStationDao.getCount();
    }
    
    public interface StationCountCallback {
        void onStationCountReceived(int count);
        void onError(String error);
    }
    
    // 按名称获取所有电台
    public LiveData<List<RadioStation>> getAllStationsByName() {
        return radioStationDao.getAllStationsByName();
    }
    
    // 按点击次数获取电台
    public LiveData<List<RadioStation>> getStationsByClickCount() {
        return radioStationDao.getStationsByClickCount();
    }
    
    // 获取点击排行前N个电台
    public LiveData<List<RadioStation>> getTopClickStations(int limit) {
        return radioStationDao.getTopClickStations(limit);
    }
    
    // 按投票数获取电台
    public LiveData<List<RadioStation>> getStationsByVotes() {
        return radioStationDao.getStationsByVotes();
    }
    
    // 获取投票排行前N个电台
    public LiveData<List<RadioStation>> getTopVoteStations(int limit) {
        return radioStationDao.getTopVoteStations(limit);
    }
    
    // 按最后更改时间获取电台
    public LiveData<List<RadioStation>> getStationsByLastChangeTime() {
        return radioStationDao.getStationsByLastChangeTime();
    }
    
    // 获取最近更新的电台（限制数量）
    public LiveData<List<RadioStation>> getRecentlyChangedStations(int limit) {
        return radioStationDao.getRecentlyChangedStations(limit);
    }
    
    // 获取最近点击的电台（限制数量）
    public LiveData<List<RadioStation>> getRecentlyPlayedStations(int limit) {
        return radioStationDao.getRecentlyPlayedStations(limit);
    }
    
    // 获取所有国家
    public LiveData<List<String>> getAllCountries() {
        return radioStationDao.getAllCountries();
    }
    
    // 获取所有国家（同步版本）
    public List<String> getAllCountriesSync() {
        return radioStationDao.getAllCountriesSync();
    }
    
    // 按国家获取电台
    public LiveData<List<RadioStation>> getStationsByCountry(String country) {
        return radioStationDao.getStationsByCountry(country);
    }
    
    // 获取所有语言
    public LiveData<List<String>> getAllLanguages() {
        return radioStationDao.getAllLanguages();
    }
    
    // 获取所有语言（同步版本）
    public List<String> getAllLanguagesSync() {
        return radioStationDao.getAllLanguagesSync();
    }
    
    // 按语言获取电台
    public LiveData<List<RadioStation>> getStationsByLanguage(String language) {
        return radioStationDao.getStationsByLanguage(language);
    }
    
    // 按语言获取电台（限制数量）
    public LiveData<List<RadioStation>> getStationsByLanguageWithLimit(String language, int limit) {
        return radioStationDao.getStationsByLanguageWithLimit(language, limit);
    }
    
    // 按语言和国家获取电台（限制数量）
    public LiveData<List<RadioStation>> getStationsByLanguageAndCountry(String language, String countryCode, int limit) {
        return radioStationDao.getStationsByLanguageAndCountry(language, countryCode, limit);
    }
    
    // 按语言和国家获取电台（不限制数量）
    public LiveData<List<RadioStation>> getStationsByLanguageAndCountry(String language, String countryCode) {
        return radioStationDao.getStationsByLanguageAndCountry(language, countryCode);
    }
    
    // 按国家获取电台（限制数量）
    public LiveData<List<RadioStation>> getStationsByCountryWithLimit(String countryCode, int limit) {
        return radioStationDao.getStationsByCountryWithLimit(countryCode, limit);
    }
    
    // 获取所有标签
    public LiveData<List<String>> getAllTags() {
        return radioStationDao.getAllTags();
    }
    
    // 按标签获取电台
    public LiveData<List<RadioStation>> getStationsByTag(String tag) {
        return radioStationDao.getStationsByTag(tag);
    }
    
    // 获取标签对应的电台数量
    public LiveData<Integer> getStationCountByTag(String tag) {
        return radioStationDao.getStationCountByTag(tag);
    }
    
    // 获取标签对应的电台数量（同步版本）
    public int getStationCountByTagSync(String tag) {
        return radioStationDao.getStationCountByTagSync(tag);
    }
    
    // 更精确的标签查询，处理特殊字符如#
    public int getStationCountByTagPreciseSync(String tag) {
        return radioStationDao.getStationCountByTagPreciseSync(tag);
    }
    
    // 获取国家对应的电台数量
    public LiveData<Integer> getStationCountByCountry(String country) {
        return radioStationDao.getStationCountByCountry(country);
    }
    
    // 获取国家对应的电台数量（同步版本）
    public int getStationCountByCountrySync(String country) {
        return radioStationDao.getStationCountByCountrySync(country);
    }
    
    // 优化的查询方法 - 一次性获取所有国家及其电台数量
    public List<CountryCount> getAllCountriesWithCountSync() {
        return radioStationDao.getAllCountriesWithCountSync();
    }
    
    // 优化的查询方法 - 一次性获取所有语言及其电台数量
    public List<LanguageCount> getAllLanguagesWithCountSync() {
        return radioStationDao.getAllLanguagesWithCountSync();
    }
    
    // 获取所有标签字符串（原始格式，包含逗号分隔的多个标签）
    public List<String> getAllTagStringsSync() {
        return radioStationDao.getAllTagStringsSync();
    }
    
    // 获取语言对应的电台数量
    public LiveData<Integer> getStationCountByLanguage(String language) {
        return radioStationDao.getStationCountByLanguage(language);
    }
    
    // 获取语言对应的电台数量（同步版本）
    public int getStationCountByLanguageSync(String language) {
        return radioStationDao.getStationCountByLanguageSync(language);
    }
    
    // 搜索电台
    public LiveData<List<RadioStation>> searchStations(String query) {
        return radioStationDao.searchStations(query);
    }
    
    // 使用FTS快速搜索电台
    public LiveData<List<RadioStation>> searchStationsFast(String query) {
        return radioStationDao.searchStationsFast(query);
    }
    
    // 使用FTS按名称快速搜索电台
    public LiveData<List<RadioStation>> searchStationsByNameFast(String query) {
        return radioStationDao.searchStationsByNameFast(query);
    }
    
    // 使用FTS按标签快速搜索电台
    public LiveData<List<RadioStation>> searchStationsByTagsFast(String query) {
        return radioStationDao.searchStationsByTagsFast(query);
    }
    
    // 使用FTS按国家快速搜索电台
    public LiveData<List<RadioStation>> searchStationsByCountryFast(String query) {
        return radioStationDao.searchStationsByCountryFast(query);
    }
    
    // 使用FTS按语言快速搜索电台
    public LiveData<List<RadioStation>> searchStationsByLanguageFast(String query) {
        return radioStationDao.searchStationsByLanguageFast(query);
    }
    
    // 按名称搜索电台
    public LiveData<List<RadioStation>> searchStationsByName(String query) {
        return radioStationDao.searchStationsByName(query);
    }
    
    // 按标签搜索电台
    public LiveData<List<RadioStation>> searchStationsByTags(String query) {
        return radioStationDao.searchStationsByTags(query);
    }
    
    // 按国家搜索电台
    public LiveData<List<RadioStation>> searchStationsByCountry(String query) {
        return radioStationDao.searchStationsByCountry(query);
    }
    
    // 按语言搜索电台
    public LiveData<List<RadioStation>> searchStationsByLanguage(String query) {
        return radioStationDao.searchStationsByLanguage(query);
    }
    
    // 按国家代码获取电台
    public LiveData<List<RadioStation>> getStationsByCountryCode(String countryCode) {
        return radioStationDao.getStationsByCountryCode(countryCode);
    }
    
    // 按精确语言获取电台
    public LiveData<List<RadioStation>> getStationsByLanguageExact(String language) {
        return radioStationDao.getStationsByLanguageExact(language);
    }
    
    // 按精确标签获取电台
    public LiveData<List<RadioStation>> getStationsByTagExact(String tag) {
        return radioStationDao.getStationsByTagExact(tag);
    }
    
    // 分页查询方法
    public DataSource.Factory<Integer, RadioStation> getAllStationsPaged() {
        return radioStationDao.getAllStationsPaged();
    }
    
    public DataSource.Factory<Integer, RadioStation> getStationsByClickCountPaged() {
        return radioStationDao.getStationsByClickCountPaged();
    }
    
    public DataSource.Factory<Integer, RadioStation> getStationsByVotesPaged() {
        return radioStationDao.getStationsByVotesPaged();
    }
    
    public DataSource.Factory<Integer, RadioStation> getStationsByLastChangeTimePaged() {
        return radioStationDao.getStationsByLastChangeTimePaged();
    }
    
    public DataSource.Factory<Integer, RadioStation> searchStationsPaged(String query) {
        return radioStationDao.searchStationsPaged(query);
    }
    
    // 根据UUID获取电台
    public RadioStation getStationByUuid(String uuid) {
        return radioStationDao.getStationById(uuid);
    }
    
    // 获取数据库更新时间戳
    public long getDatabaseUpdateTime() {
        UpdateTimestamp timestamp = updateTimestampDao.getTimestamp();
        if (timestamp != null) {
            return timestamp.last_update_timestamp;
        }
        return 0;
    }
    
    // 更新数据库时间戳
    public void updateDatabaseTimestamp() {
        updateTimestampDao.updateTimestamp(System.currentTimeMillis());
    }
    
    // 更新数据库时间戳（使用指定时间戳）
    public void updateDatabaseTimestamp(long timestamp) {
        updateTimestampDao.updateTimestamp(timestamp);
    }
    
    // 更新数据库时间戳并保存到SharedPreferences
    public void updateDatabaseTimestamp(Context context) {
        long currentTime = System.currentTimeMillis();
        updateTimestampDao.updateTimestamp(currentTime);
        
        // 获取电台数量
        int stationCount = radioStationDao.getCount();
        
        // 同时更新SharedPreferences中的本地数据库最后更新时间和电台数量
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
        String formattedTime = dateFormat.format(new java.util.Date(currentTime));
        
        SharedPreferences prefs = context.getSharedPreferences("net.programmierecke.radiodroid2_preferences", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("local_database_last_update", formattedTime);
        editor.putInt("local_database_station_count", stationCount);
        editor.apply();
        
        Log.d(TAG, "Updated database timestamp and station count to SharedPreferences: " + formattedTime + ", " + stationCount);
    }
    
    // 确保update_timestamp表存在并有有效数据
    public void ensureUpdateTimestampTable() {
        try {
            SupportSQLiteDatabase db = RadioDroidDatabase.getDatabase(context).getOpenHelper().getWritableDatabase();
            
            // 检查update_timestamp表是否存在
            boolean tableExists = false;
            android.database.Cursor cursor = null;
            try {
                cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='update_timestamp'");
                tableExists = cursor != null && cursor.getCount() > 0;
            } catch (Exception e) {
                Log.w(TAG, "Error checking if update_timestamp table exists", e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            
            if (!tableExists) {
                Log.d(TAG, "update_timestamp table does not exist, creating it");
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `update_timestamp` (`id` INTEGER NOT NULL, `last_update_timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                );
                db.execSQL(
                    "INSERT OR REPLACE INTO `update_timestamp` (`id`, `last_update_timestamp`) VALUES (1, 0)"
                );
                Log.d(TAG, "Created update_timestamp table with default timestamp 0");
            } else {
                Log.d(TAG, "update_timestamp table already exists");
                
                // 检查表中是否有数据
                UpdateTimestamp timestamp = updateTimestampDao.getTimestamp();
                if (timestamp == null) {
                    Log.d(TAG, "update_timestamp table is empty, inserting default row");
                    db.execSQL(
                        "INSERT OR REPLACE INTO `update_timestamp` (`id`, `last_update_timestamp`) VALUES (1, 0)"
                    );
                } else {
                    Log.d(TAG, "update_timestamp table has data, timestamp: " + timestamp.last_update_timestamp);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error ensuring update_timestamp table", e);
        }
    }
    
    // 同步回调接口
    public interface SyncCallback {
        void onProgress(String message);
        void onProgress(String message, int current, int total);
        void onSuccess(String message);
        void onError(String error);
        boolean onConfirmReplace(String message, int tempCount, int mainCount);
    }
    
    // 进度回调接口
    public interface IProgressCallback {
        void onProgress(String message, int current, int total);
        void onSuccess(String message);
        void onFailure(String error);
    }
    
    // 关闭数据库连接
    public void closeDatabase() {
        try {
            // 直接关闭RadioDroidDatabase的静态实例，避免创建新实例
            RadioDroidDatabase.closeInstance();
            
            // 重置Repository实例
            INSTANCE = null;
            Log.d(TAG, "Database connection closed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error closing database connection", e);
        }
    }
    
    // 重新初始化数据库
    public void reinitializeDatabase(Context context) {
        try {
            // 关闭现有数据库连接
            closeDatabase();
            
            // 强制重新创建数据库实例
            RadioDroidDatabase newDb = RadioDroidDatabase.forceRecreateDatabase(context);
            
            // 重新初始化DAO对象
            this.radioStationDao = newDb.radioStationDao();
            this.updateTimestampDao = newDb.updateTimestampDao();
            
            // 重新创建临时数据库
            RadioDroidDatabase tempDatabase = Room.databaseBuilder(context.getApplicationContext(),
                    RadioDroidDatabase.class, "radio_droid_database_temp")
                    .addMigrations(RadioDroidDatabase.MIGRATION_3_4, RadioDroidDatabase.MIGRATION_4_5)
                    .build();
            this.tempRadioStationDao = tempDatabase.radioStationDao();
            
            Log.d(TAG, "Database reinitialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error reinitializing database", e);
            throw new RuntimeException("Failed to reinitialize database: " + e.getMessage(), e);
        }
    }
    
    // 检查网络连接是否可用
    private boolean isNetworkAvailable(Context context) {
        try {
            ConnectivityManager connectivityManager = 
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            
            if (connectivityManager == null) {
                Log.e(TAG, "ConnectivityManager is null");
                return false;
            }
            
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            boolean isConnected = activeNetworkInfo != null && activeNetworkInfo.isConnected();
            
            Log.d(TAG, "网络连接状态: " + (isConnected ? "已连接" : "未连接"));
            return isConnected;
        } catch (Exception e) {
            Log.e(TAG, "检查网络连接时出错", e);
            return false;
        }
    }
    
    // 检查电池电量
    private void checkBatteryLevel(Context context, SyncCallback callback) {
        try {
            BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            
            if (batteryManager == null) {
                Log.w(TAG, "BatteryManager is null, 无法检查电池电量");
                return;
            }
            
            int batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            
            if (batteryLevel == Integer.MIN_VALUE) {
                // 如果无法通过BatteryManager获取电量，尝试使用Intent方式
                Intent batteryStatus = context.registerReceiver(null, 
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                
                if (batteryStatus != null) {
                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    
                    if (level > 0 && scale > 0) {
                        batteryLevel = (level * 100) / scale;
                    }
                }
            }
            
            Log.d(TAG, "当前电池电量: " + batteryLevel + "%");
            
            // 如果电量低于20%，显示警告
            if (batteryLevel < 20 && batteryLevel > 0) {
                String warning = "电量较低 (" + batteryLevel + "%)，建议连接充电器后再更新数据库";
                Log.w(TAG, warning);
                callback.onProgress(warning);
            }
            
            // 如果电量低于5%，阻止更新
            if (batteryLevel < 5 && batteryLevel > 0) {
                String error = "电量过低 (" + batteryLevel + "%)，请连接充电器后再更新数据库";
                Log.e(TAG, error);
                callback.onError(error);
                throw new RuntimeException(error);
            }
        } catch (Exception e) {
            Log.e(TAG, "检查电池电量时出错", e);
            // 电池检查失败不应该阻止更新，只记录错误
            callback.onProgress("无法检查电池电量，继续更新");
        }
    }
    
    // 检查存储空间
    private void checkStorageSpace(Context context, SyncCallback callback) {
        try {
            // 检查内部存储空间
            File internalDir = context.getFilesDir();
            StatFs internalStat = new StatFs(internalDir.getPath());
            long internalAvailable = internalStat.getAvailableBytes();
            long internalRequired = 50 * 1024 * 1024; // 至少需要50MB内部存储空间
            
            Log.d(TAG, "内部存储可用空间: " + (internalAvailable / (1024 * 1024)) + "MB");
            
            if (internalAvailable < internalRequired) {
                String error = "内部存储空间不足，至少需要50MB可用空间";
                Log.e(TAG, error);
                callback.onError(error);
                throw new RuntimeException(error);
            }
            
            // 检查外部存储空间（如果可用）
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File externalDir = Environment.getExternalStorageDirectory();
                StatFs externalStat = new StatFs(externalDir.getPath());
                long externalAvailable = externalStat.getAvailableBytes();
                long externalRequired = 100 * 1024 * 1024; // 至少需要100MB外部存储空间
                
                Log.d(TAG, "外部存储可用空间: " + (externalAvailable / (1024 * 1024)) + "MB");
                
                if (externalAvailable < externalRequired) {
                    String warning = "外部存储空间较少，建议释放一些空间以确保更新顺利完成";
                    Log.w(TAG, warning);
                    callback.onProgress(warning);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查存储空间时出错", e);
            // 存储检查失败不应该阻止更新，只记录错误
            callback.onProgress("无法检查存储空间，继续更新");
        }
    }
}