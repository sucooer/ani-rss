<template>
  <SettingsItem label="自动刮削">
    <el-switch v-model="props.config['scrape']"/>
  </SettingsItem>
  <SettingsItem label="刮削图片">
    <div>
      <el-switch v-model="props.config['scrapeImage']"/>
      <br/>
      <el-text class="mx-1" size="small">
        关闭后仅刮削 NFO 元数据，不下载海报、背景图、缩略图等图片
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem label="仅刮削主背景图">
    <div>
      <el-switch v-model="props.config['scrapeImageOnlyMainFanart']"/>
      <br/>
      <el-text class="mx-1" size="small">
        开启后只保存一张主背景图（fanart），不下载额外的背景图
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem label="追更天数">
    <div>
      <el-input-number v-model="props.config['followDay']" :min="1">
        <template #suffix>
          天
        </template>
      </el-input-number>
      <br/>
      <el-text class="mx-1" size="small">
        自动强制刮削最近更新集的元数据
      </el-text>
    </div>
  </SettingsItem>
  <SettingsItem label="更多">
    <el-checkbox label="bangumi.ini" v-model="props.config['bangumiIniEnabled']"/>
  </SettingsItem>
  <SettingsItem label="TmdbApi">
    <el-input v-model:model-value="props.config['tmdbApi']" placeholder="https://api.themoviedb.org"/>
  </SettingsItem>
  <SettingsItem label="TmdbApiKey">
    <el-input v-model:model-value="props.config['tmdbApiKey']" placeholder="请自备 API 密钥, 留空使用系统默认"/>
  </SettingsItem>
  <SettingsItem label="TmdbImage">
    <el-input v-model:model-value="props.config['tmdbImage']" placeholder="https://image.tmdb.org"/>
  </SettingsItem>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue";

import {ElText} from "element-plus";

let props = defineProps(['config'])
</script>