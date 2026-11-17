<script setup lang="ts">
import {ref,onMounted} from 'vue';
import {request} from '../api/http';
import type {Health} from '../api/generated';
const status=ref('正在连接本机后端');
onMounted(async()=>{try{const h=await request<Health>('/health');status.value=h.database_ready?'后端已连接，空数据库已初始化':'数据库尚未就绪';}catch{status.value='后端未连接，请按启动说明启动后端';}});
</script>
<template><p class="status">{{status}}</p></template>
