<template>
  <div>
    <el-card shadow="never">
      <div class="toolbar">
        <span class="toolbar-title">巡检模板列表</span>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建模板</el-button>
      </div>

      <el-table v-loading="loading" :data="templates" stripe>
        <el-table-column prop="templateNo" label="模板编号" min-width="140" />
        <el-table-column prop="templateName" label="模板名称" min-width="160" />
        <el-table-column label="版本" width="80">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeTemplate(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建 / 编辑模板（API-D05：编辑生成新版本） -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建巡检模板' : `编辑巡检模板（当前 v${editingVersion}，保存后生成新版本）`"
      width="720px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="form.templateName" placeholder="如 洞身段日常巡检" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="巡检项">
          <div class="items-box">
            <div v-for="(item, idx) in form.items" :key="idx" class="item-row">
              <el-input v-model="item.itemName" placeholder="项目名（必填）" class="col-name" />
              <el-input v-model="item.checkContent" placeholder="检查内容" class="col-content" />
              <el-input v-model="item.judgeStandard" placeholder="判定标准" class="col-judge" />
              <el-button link type="danger" :icon="Delete" @click="form.items.splice(idx, 1)" />
            </div>
            <el-button link type="primary" :icon="Plus" @click="addItem">添加巡检项</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'

import {
  createTemplateApi,
  deleteTemplateApi,
  getTemplateDetailApi,
  getTemplateListApi,
  updateTemplateApi,
} from '@/api/patrol'
import type { PatrolTemplateVO } from '@/types/patrol'

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const templates = ref<PatrolTemplateVO[]>([])

async function loadTemplates() {
  loading.value = true
  try {
    templates.value = await getTemplateListApi()
  } finally {
    loading.value = false
  }
}

/* ---------------- 新建 / 编辑 ---------------- */
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number>()
const editingVersion = ref<number>(0)

interface ItemForm {
  itemName: string
  checkContent?: string
  judgeStandard?: string
  sort: number
}

interface TemplateForm {
  templateName: string
  remark: string
  items: ItemForm[]
}

const emptyForm = (): TemplateForm => ({
  templateName: '',
  remark: '',
  items: [{ itemName: '', checkContent: '', judgeStandard: '', sort: 0 }],
})

const form = reactive<TemplateForm>(emptyForm())

const formRules: FormRules = {
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
}

function addItem() {
  form.items.push({ itemName: '', checkContent: '', judgeStandard: '', sort: form.items.length })
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = undefined
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

async function openEdit(row: PatrolTemplateVO) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  editingVersion.value = row.version
  const detail = await getTemplateDetailApi(row.id)
  Object.assign(form, {
    templateName: detail.template.templateName,
    remark: detail.template.remark ?? '',
    items: detail.items.length
      ? detail.items.map((i) => ({
          itemName: i.itemName,
          checkContent: i.checkContent,
          judgeStandard: i.judgeStandard,
          sort: i.sort,
        }))
      : [{ itemName: '', checkContent: '', judgeStandard: '', sort: 0 }],
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const validItems = form.items.filter((i) => i.itemName.trim())
  if (validItems.length === 0) {
    ElMessage.warning('请至少填写一个巡检项')
    return
  }
  const payload = {
    templateName: form.templateName,
    remark: form.remark || null,
    items: validItems.map((i, idx) => ({
      itemName: i.itemName,
      checkContent: i.checkContent || null,
      judgeStandard: i.judgeStandard || null,
      sort: idx,
    })),
  }
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createTemplateApi(payload)
      ElMessage.success('创建成功')
    } else {
      await updateTemplateApi(editingId.value as number, payload)
      ElMessage.success('已保存为新版本')
    }
    dialogVisible.value = false
    await loadTemplates()
  } catch {
    // 失败提示由 http.ts 统一处理
  } finally {
    submitting.value = false
  }
}

async function removeTemplate(row: PatrolTemplateVO) {
  try {
    await ElMessageBox.confirm(`确定删除巡检模板「${row.templateName}」吗？`, '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  await deleteTemplateApi(row.id)
  ElMessage.success('删除成功')
  await loadTemplates()
}

onMounted(() => {
  void loadTemplates()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.items-box {
  width: 100%;
}

.item-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.col-name {
  width: 160px;
  flex: none;
}

.col-content {
  flex: 1.2;
}

.col-judge {
  flex: 1;
}
</style>
