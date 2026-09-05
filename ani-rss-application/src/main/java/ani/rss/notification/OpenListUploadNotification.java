package ani.rss.notification;

import ani.rss.commons.FileUtils;
import ani.rss.commons.GsonStatic;
import ani.rss.config.OpenListConfig;
import ani.rss.entity.Ani;
import ani.rss.entity.NotificationConfig;
import ani.rss.entity.OpenListFileInfo;
import ani.rss.entity.web.Header;
import ani.rss.enums.NotificationStatusEnum;
import ani.rss.enums.StringEnum;
import ani.rss.service.DownloadService;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.OpenListUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.http.HttpConfig;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class OpenListUploadNotification implements BaseNotification {
    /**
     * 上传配置
     */
    private final HttpConfig httpConfig = new HttpConfig()
            .setBlockSize(8192);

    @Getter
    private NotificationConfig notificationConfig;

    private final OpenListUtil openListUtil = OpenListUtil.getInstance(new OpenListConfig() {
        @Override
        public String getServer() {
            return getNotificationConfig().getOpenListUploadHost();
        }

        @Override
        public String getApiKey() {
            return getNotificationConfig().getOpenListUploadApiKey();
        }
    });

    /**
     * 测试
     *
     * @param notificationConfig     通知配置
     * @param ani                    订阅
     * @param text                   通知内容
     * @param notificationStatusEnum 通知状态
     */
    @Override
    public void test(NotificationConfig notificationConfig, Ani ani, String text, NotificationStatusEnum notificationStatusEnum) {
        List<NotificationStatusEnum> statusList = notificationConfig.getStatusList();
        Assert.isTrue(statusList.contains(NotificationStatusEnum.DOWNLOAD_END), "请设置为下载完成通知");

        String openListUploadHost = notificationConfig.getOpenListUploadHost();
        String openListUploadApiKey = notificationConfig.getOpenListUploadApiKey();

        HttpReq.get(openListUploadHost + "/api/me")
                .header(Header.AUTHORIZATION, openListUploadApiKey)
                .then(res -> {
                    HttpReq.assertStatus(res);
                    JsonObject jsonObject = GsonStatic.fromJson(res.body(), JsonObject.class);
                    int code = jsonObject.get("code").getAsInt();
                    String message = jsonObject.get("message").getAsString();
                    Assert.isTrue(code == 200, message);
                });
    }

    /**
     * 发送通知
     *
     * @param notificationConfig     通知配置
     * @param ani                    订阅
     * @param text                   通知内容
     * @param notificationStatusEnum 通知状态
     * @return 是否成功
     */
    @Override
    public Boolean send(NotificationConfig notificationConfig, Ani ani, String text, NotificationStatusEnum notificationStatusEnum) {
        if (NotificationStatusEnum.DOWNLOAD_END != notificationStatusEnum) {
            log.info("OpenListUpload 仅支持下载完成通知");
            return true;
        }

        ani = ObjectUtil.clone(ani);

        this.notificationConfig = notificationConfig;

        String openListUploadPath = notificationConfig.getOpenListUploadPath();
        String openListUploadOvaPath = notificationConfig.getOpenListUploadOvaPath();
        Boolean deleteOldEpisode = notificationConfig.getOpenListUploadDeleteOldEpisode();

        // 本地位置
        DownloadService downloadService = SpringUtil.getBean(DownloadService.class);
        String localPath = downloadService.getDownloadPath(ani);

        // 新的位置; 设置自定义下载位置同时启用, 用以获取新的位置
        boolean ova = ani.getOva();
        String downloadPathTemplate = ova ? openListUploadOvaPath : openListUploadPath;

        Boolean customUploadEnable = ani.getCustomUploadEnable();
        if (customUploadEnable) {
            // 自定义上传位置
            downloadPathTemplate = ani.getCustomUploadPathTarget();
        }

        String target = downloadService.getDownloadPath(ani, downloadPathTemplate);
        target = ReUtil.replaceAll(target, "^[A-z]:", "");

        if (ova) {
            uploadOva(localPath, target);
            return true;
        }

        if (deleteOldEpisode) {
            deleteOldEpisode(localPath, target);
        }

        upload(localPath, target);

        // 电视剧的 tvshow.nfo、海报、背景图等资源位于 Season 目录的上一级
        uploadParentFiles(localPath, target);

        return true;
    }

    public void uploadOva(String localFilePath, String cloudFilePath) {
        uploadFiles(localFilePath, cloudFilePath, false, false,
                Boolean.TRUE.equals(notificationConfig.getOpenListUploadDeleteLocalFile()));
    }

    /**
     * 上传电视剧目录的上一级资源，例如 tvshow.nfo、poster、fanart 等。
     * 只上传 NFO 和图片，避免把其他季度的视频上传到当前季度目录的上一级。
     */
    private void uploadParentFiles(String localFilePath, String cloudFilePath) {
        File parent = new File(localFilePath).getParentFile();
        if (parent == null) {
            return;
        }

        uploadFiles(parent.getAbsolutePath(), getParentPath(cloudFilePath), false, true, false);
    }

    /**
     * 获取远端目录的上一级路径
     */
    private String getParentPath(String path) {
        String normalizedPath = path.replace('\\', '/');
        int index = normalizedPath.lastIndexOf('/');
        return index > 0 ? normalizedPath.substring(0, index) : "/";
    }

    public void deleteOldEpisode(String localFilePath, String cloudFilePath) {
        // 本地文件列表
        List<File> localFileList = FileUtils.listFileList(localFilePath)
                .stream()
                .filter(FileUtil::isFile)
                .toList();

        // 存储为MAP便于根据文件名寻找
        Map<String, File> localFileMap = localFileList.stream()
                .collect(Collectors.toMap(File::getName, i -> i));

        // 本地集数列表
        Set<String> localEpisodeSet = localFileList
                .stream()
                .map(File::getName)
                .filter(name -> FileUtils.isVideoFormat(name) || FileUtils.isSubtitleFormat(name))
                .filter(name -> ReUtil.contains(StringEnum.SEASON_REG, name))
                .map(name -> ReUtil.get(StringEnum.SEASON_REG, name, 0))
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        List<OpenListFileInfo> fileInfos = openListUtil.fsList(cloudFilePath, true);
        for (OpenListFileInfo fileInfo : fileInfos) {
            String name = fileInfo.getName();
            if (!FileUtils.isVideoFormat(name) && !FileUtils.isSubtitleFormat(name)) {
                // 非视频与字幕文件
                continue;
            }
            if (!ReUtil.contains(StringEnum.SEASON_REG, name)) {
                // 确保命名
                continue;
            }

            if (localFileMap.containsKey(name)) {
                long cloudFileLength = fileInfo.getSize();
                long localFileLength = localFileMap.get(name).length();
                if (cloudFileLength == localFileLength) {
                    // 文件名与大小一致 跳过删除
                    continue;
                }
            }

            String episode = ReUtil.get(StringEnum.SEASON_REG, name, 0);
            episode = episode.toUpperCase();
            if (!localEpisodeSet.contains(episode)) {
                // 新位置没有旧的同集文件 不需要删除
                continue;
            }

            log.info("因洗版需要删除: {}", name);

            // 删除 洗版
            openListUtil.fsRemove(cloudFilePath, List.of(name));
        }
    }

    private void upload(String localFilePath, String cloudFilePath) {
        uploadFiles(localFilePath, cloudFilePath, true, false,
                Boolean.TRUE.equals(notificationConfig.getOpenListUploadDeleteLocalFile()));
    }

    /**
     * 上传指定目录下的文件。
     *
     * @param localFilePath       本地目录
     * @param cloudFilePath       OpenList 目标目录
     * @param requireEpisodeName  视频和字幕是否必须包含集数命名
     * @param metadataOnly        是否只上传 NFO 和图片
     * @param deleteLocalFile     上传完成后是否删除本地文件
     */
    private void uploadFiles(String localFilePath, String cloudFilePath,
                             boolean requireEpisodeName, boolean metadataOnly, boolean deleteLocalFile) {
        // 云端文件列表 存储为 MAP 便于根据文件名寻找
        Map<String, OpenListFileInfo> cloudFileMap = openListUtil.fsList(cloudFilePath, true)
                .stream()
                .collect(Collectors.toMap(OpenListFileInfo::getName, i -> i));

        for (File file : FileUtils.listFileList(localFilePath)) {
            if (!file.isFile() || !isUploadFile(file, requireEpisodeName, metadataOnly)) {
                continue;
            }

            if (isImageTooLarge(file)) {
                Long maxSize = notificationConfig.getOpenListUploadImageMaxSize();
                log.info("图片超过 OpenList 上传大小限制，跳过: {} ({} bytes > {} KiB)",
                        file, file.length(), maxSize);
                continue;
            }

            String name = file.getName();
            OpenListFileInfo cloudFileInfo = cloudFileMap.get(name);
            if (cloudFileInfo != null && cloudFileInfo.getSize() != null
                    && file.length() == cloudFileInfo.getSize()) {
                // 文件名与大小一致 跳过上传
                continue;
            }

            log.info("文件上传: {} => {}", file, cloudFilePath);
            uploadFile(file.getAbsolutePath(), cloudFilePath);

            if (deleteLocalFile) {
                log.info("删除本地文件 {}", file);
                FileUtil.del(file);
            }
        }
    }

    /**
     * 判断文件是否属于 OpenList 同步范围
     */
    private boolean isUploadFile(File file, boolean requireEpisodeName, boolean metadataOnly) {
        String name = file.getName();
        boolean nfo = isNfoFormat(name);
        boolean image = FileUtils.isImageFormat(name);

        if (metadataOnly) {
            return nfo || image;
        }

        boolean videoOrSubtitle = FileUtils.isVideoFormat(name) || FileUtils.isSubtitleFormat(name);
        if (!videoOrSubtitle && !nfo && !image) {
            return false;
        }

        // 仅限制视频和字幕的命名；season.nfo、poster 等资源不带 SxxExx 也应同步。
        return !requireEpisodeName || nfo || image || ReUtil.contains(StringEnum.SEASON_REG, name);
    }

    private boolean isNfoFormat(String filename) {
        return "nfo".equalsIgnoreCase(FileUtil.extName(filename));
    }

    /**
        * 图片大小限制只对图片生效，单位为 KiB，0 或空值表示不限制。
     */
    private boolean isImageTooLarge(File file) {
        if (!FileUtils.isImageFormat(file.getName())) {
            return false;
        }

        Long maxSize = notificationConfig.getOpenListUploadImageMaxSize();
        if (maxSize == null || maxSize <= 0 || maxSize > Long.MAX_VALUE / 1024L) {
            return false;
        }

        return file.length() > maxSize * 1024L;
    }

    /**
     * 上传文件
     *
     * @param localFilePath 本地文件位置
     * @param cloudFilePath 云端文件位置
     */
    private void uploadFile(String localFilePath, String cloudFilePath) {
        Assert.isTrue(FileUtil.exist(localFilePath), "文件不存在 {}", localFilePath);

        if (FileUtil.isDirectory(localFilePath)) {
            List<File> files = FileUtils.listFileList(localFilePath);
            for (File file : files) {
                uploadFile(file.getAbsolutePath(), cloudFilePath);
            }
            return;
        }

        String openListUploadHost = notificationConfig.getOpenListUploadHost();
        String openListUploadApiKey = notificationConfig.getOpenListUploadApiKey();

        String url = StrUtil.format("{}/api/fs/put", openListUploadHost);


        String filename = FileUtil.getName(localFilePath);

        HttpReq
                .put(url)
                .timeout(1000 * 60 * 2)
                .setConfig(httpConfig)
                .header(Header.AUTHORIZATION, openListUploadApiKey)
                .header("As-Task", "false")
                .header("File-Path", URLUtil.encode(cloudFilePath + "/" + filename))
                .contentType("application/octet-stream")
                .body(ResourceUtil.getResourceObj(localFilePath))
                .then(res -> {
                    Assert.isTrue(res.isOk(), "上传失败 {} 状态码:{}", localFilePath, res.getStatus());
                    JsonObject jsonObject = GsonStatic.fromJson(res.body(), JsonObject.class);
                    int code = jsonObject.get("code").getAsInt();
                    log.info(jsonObject.toString());
                    Assert.isTrue(code == 200, "上传失败 {} 状态码:{}", localFilePath, code);

                    log.info("OpenList 上传完成 {}", filename);
                });
    }
}
