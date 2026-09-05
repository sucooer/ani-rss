<template>
  <template v-if="notificationConfig['notificationType'] === 'OPEN_LIST_UPLOAD'">
    <SettingsItem label="Host">
      <el-input v-model="notificationConfig['openListUploadHost']" placeholder="http://127.0.0.1:5244"/>
    </SettingsItem>
    <SettingsItem label="ApiKey">
      <el-input v-model="notificationConfig['openListUploadApiKey']" placeholder="openlist-xxxxxx"/>
    </SettingsItem>
    <SettingsItem label="上传位置">
      <el-input
          v-model="notificationConfig['openListUploadPath']"
          placeholder="/115/Media/番剧/${title}/Season ${season}"/>
    </SettingsItem>
    <SettingsItem label="上传位置 (剧场版)">
      <el-input
          v-model="notificationConfig['openListUploadOvaPath']"
          placeholder="/115/Media/剧场版/${title}"/>
    </SettingsItem>
    <SettingsItem label="图片大小限制">
      <div>
        <el-input-number
            v-model="notificationConfig['openListUploadImageMaxSize']"
            :min="0"
            :step="1"
            class="notification-input-width"
        >
          <template #suffix>
            <span>KiB</span>
          </template>
        </el-input-number>
        <br/>
        <el-text class="mx-1" size="small">
          仅限制图片文件，0 表示不限制；视频、字幕和 NFO 不受此限制
        </el-text>
      </div>
    </SettingsItem>
    <SettingsItem label="其他">
      <div>
        <el-checkbox v-model="notificationConfig['openListUploadDeleteLocalFile']" label="上传完成后删除本地文件"/>
        <br/>
        <el-checkbox v-model="notificationConfig['openListUploadDeleteOldEpisode']" label="删除旧的同集文件"/>
        <br/>
        <el-text class="mx-1" size="small">
          自动将目标位置存在的同集文件删除, 可达到洗版的效果
        </el-text>
      </div>
    </SettingsItem>
  </template>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue";

let props = defineProps(['notificationConfig', 'config'])
</script>
