<template>
  <div>
    <!-- 筛选区 -->
    <el-card shadow="never" class="mb16">
      <el-form inline @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="用户名 / 姓名"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item class="toolbar-right">
          <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表区 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column prop="phone" label="手机号" width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="150">
          <template #default="{ row }">
            <template v-if="row.roleNames?.length">
              <el-tag v-for="name in row.roleNames" :key="name" size="small" class="mr4">
                {{ name }}
              </el-tag>
            </template>
            <template v-else>-</template>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" width="170">
          <!-- TODO[契约]：API-A06 出参未明确是否含最近登录时间，后端未返回时展示 "-" -->
          <template #default="{ row }">{{ row.lastLoginTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="warning" @click="resetPassword(row)">重置密码</el-button>
            <el-button
              link
              type="danger"
              :disabled="row.id === auth.user?.id"
              :title="row.id === auth.user?.id ? '不能删除当前登录账号' : ''"
              @click="removeUser(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="mt16"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        @size-change="loadList"
        @current-change="loadList"
      />
    </el-card>

    <!-- 新增 / 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增用户' : '编辑用户'"
      width="480px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="登录账号"
            :disabled="dialogMode === 'edit'"
          />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="选填" />
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple placeholder="选择角色" style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="role.roleName" :value="role.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span v-if="dialogMode === 'create'" class="dialog-tip">初始密码由后端策略生成（API-A07）</span>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'

import {
  addUserApi,
  deleteUserApi,
  getRoleListApi,
  getUserPageApi,
  resetPasswordApi,
  updateUserApi,
  updateUserStatusApi,
} from '@/api/sys'
import { useAuthStore } from '@/stores/auth'
import type { SysRoleVO, SysUserCreateForm, SysUserVO } from '@/types/api'

const auth = useAuthStore()

/* ---------------- 列表与筛选（API-A06 分页：pageNum/pageSize/keyword/status） ---------------- */
const loading = ref(false)
const list = ref<SysUserVO[]>([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: '' as number | '',
})

async function loadList() {
  loading.value = true
  try {
    const data = await getUserPageApi({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: typeof query.status === 'number' ? query.status : undefined,
    })
    list.value = data?.list ?? []
    total.value = data?.total ?? 0
  } catch {
    // 失败提示由 http.ts 统一处理
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  void loadList()
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  query.pageNum = 1
  void loadList()
}

/* ---------------- 新增 / 编辑（API-A07/A08） ---------------- */
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const roles = ref<SysRoleVO[]>([])

const form = reactive<SysUserCreateForm>({ username: '', realName: '', phone: '', roleIds: [] })

const formRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  form.username = ''
  form.realName = ''
  form.phone = ''
  form.roleIds = []
  dialogVisible.value = true
}

function openEdit(row: SysUserVO) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.username = row.username
  form.realName = row.realName
  form.phone = row.phone ?? ''
  form.roleIds = row.roleIds ? [...row.roleIds] : []
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await addUserApi({
        username: form.username,
        realName: form.realName,
        phone: form.phone || undefined,
        roleIds: form.roleIds,
      })
      ElMessage.success('新增成功（初始密码由后端策略生成）')
    } else if (editingId.value != null) {
      await updateUserApi(editingId.value, {
        realName: form.realName,
        phone: form.phone || undefined,
        roleIds: form.roleIds,
      })
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    void loadList()
  } catch {
    // 失败提示由 http.ts 统一处理
  } finally {
    submitting.value = false
  }
}

/* ---------------- 启停（API-A10） ---------------- */
async function toggleStatus(row: SysUserVO) {
  const target = row.status === 1 ? 0 : 1
  const actionText = target === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定${actionText}用户「${row.username}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await updateUserStatusApi(row.id, target)
  ElMessage.success(`${actionText}成功`)
  void loadList()
}

/* ---------------- 重置密码（API-A11，重置后强制首次登录改密） ---------------- */
async function resetPassword(row: SysUserVO) {
  try {
    await ElMessageBox.confirm(
      `确定重置用户「${row.username}」的密码吗？重置后用户首次登录需修改密码。`,
      '提示',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await resetPasswordApi(row.id)
  ElMessage.success('密码已重置')
}

/* ---------------- 删除（API-A09，逻辑删；后端校验不能删除自己） ---------------- */
async function removeUser(row: SysUserVO) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？删除为逻辑删除。`, '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  await deleteUserApi(row.id)
  ElMessage.success('删除成功')
  if (list.value.length === 1 && query.pageNum > 1) {
    query.pageNum -= 1
  }
  void loadList()
}

onMounted(() => {
  void loadList()
  // 角色下拉选项（API-A12）
  getRoleListApi()
    .then((data) => {
      roles.value = data ?? []
    })
    .catch(() => {
      roles.value = []
    })
})
</script>

<style scoped>
.toolbar-right {
  float: right;
  margin-right: 0;
}

.mt16 {
  margin-top: 16px;
}

.dialog-tip {
  float: left;
  font-size: 12px;
  color: #909399;
  line-height: 32px;
}
</style>
