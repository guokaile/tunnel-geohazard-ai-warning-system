<template>
  <div>
    <el-card shadow="never">
      <div class="toolbar">
        <span class="toolbar-title">角色列表</span>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增角色</el-button>
      </div>

      <el-table v-loading="loading" :data="roles" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="roleCode" label="角色编码" min-width="140" />
        <el-table-column prop="roleName" label="角色名称" min-width="120" />
        <el-table-column prop="remark" label="备注" min-width="180">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openPermDialog(row)">分配权限</el-button>
            <el-button link type="danger" @click="removeRole(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增 / 编辑角色（API-A13/A14） -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增角色' : '编辑角色'"
      width="460px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input
            v-model="form.roleCode"
            placeholder="如 DISPATCHER"
            :disabled="dialogMode === 'edit'"
          />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="如 值班调度" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限（API-A16/A17/A18） -->
    <el-dialog
      v-model="permDialogVisible"
      :title="`分配权限：${permRole?.roleName ?? ''}`"
      width="520px"
    >
      <el-tree
        ref="treeRef"
        v-loading="permLoading"
        class="perm-tree"
        :data="permTree"
        node-key="id"
        show-checkbox
        default-expand-all
        :props="{ label: 'permName', children: 'children' }"
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="savePermissions">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElTree, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

import {
  addRoleApi,
  assignRolePermissionsApi,
  deleteRoleApi,
  getPermissionTreeApi,
  getRoleListApi,
  getRolePermissionsApi,
  updateRoleApi,
} from '@/api/sys'
import { STATIC_PERMISSION_TREE } from '@/mock/permissions'
import type { PermissionNode, SysRoleForm, SysRoleVO } from '@/types/api'

/* ---------------- 角色列表（API-A12：全部角色，不分页） ---------------- */
const loading = ref(false)
const roles = ref<SysRoleVO[]>([])

async function loadRoles() {
  loading.value = true
  try {
    const data = await getRoleListApi()
    roles.value = data ?? []
  } catch {
    roles.value = []
  } finally {
    loading.value = false
  }
}

/* ---------------- 新增 / 编辑（API-A13/A14） ---------------- */
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SysRoleForm>({ roleCode: '', roleName: '', remark: '' })

const formRules: FormRules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  form.roleCode = ''
  form.roleName = ''
  form.remark = ''
  dialogVisible.value = true
}

function openEdit(row: SysRoleVO) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.roleCode = row.roleCode
  form.roleName = row.roleName
  form.remark = row.remark ?? ''
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await addRoleApi({
        roleCode: form.roleCode,
        roleName: form.roleName,
        remark: form.remark || undefined,
      })
      ElMessage.success('新增成功')
    } else if (editingId.value != null) {
      await updateRoleApi(editingId.value, {
        roleCode: form.roleCode,
        roleName: form.roleName,
        remark: form.remark || undefined,
      })
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    void loadRoles()
  } catch {
    // 失败提示由 http.ts 统一处理
  } finally {
    submitting.value = false
  }
}

/* ---------------- 删除（API-A15，后端校验无用户占用） ---------------- */
async function removeRole(row: SysRoleVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除角色「${row.roleName}」吗？存在用户占用时后端将拒绝删除。`,
      '提示',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await deleteRoleApi(row.id)
  ElMessage.success('删除成功')
  void loadRoles()
}

/* ---------------- 权限分配（API-A16/A17/A18） ---------------- */
const permDialogVisible = ref(false)
const permLoading = ref(false)
const permSaving = ref(false)
const permRole = ref<SysRoleVO | null>(null)
const permTree = ref<PermissionNode[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()

async function openPermDialog(row: SysRoleVO) {
  permRole.value = row
  permDialogVisible.value = true
  permLoading.value = true
  try {
    // 权限树（API-A18）；后端不可用时回退《4》4.7 权限点清单的静态模拟数据
    try {
      permTree.value = await getPermissionTreeApi({ skipErrorMessage: true })
    } catch {
      console.warn(
        '[TODO] GET /sys/permissions（API-A18）不可用，当前使用《4.接口设计说明书》4.7 权限点静态模拟数据，后端实现后应移除该 fallback（src/mock/permissions.ts）。',
      )
      permTree.value = STATIC_PERMISSION_TREE
    }
    // 回显角色已分配权限（API-A16：权限点 id 列表，与 tree node-key=id 对应）
    let permIds: number[] = []
    try {
      permIds = (await getRolePermissionsApi(row.id, { skipErrorMessage: true })) ?? []
    } catch {
      console.warn(`[TODO] GET /sys/roles/${row.id}/permissions（API-A16）不可用，权限回显为空。`)
    }
    await nextTick()
    treeRef.value?.setCheckedKeys(permIds)
  } finally {
    permLoading.value = false
  }
}

async function savePermissions() {
  if (!permRole.value || !treeRef.value) return
  // API-A17 入参 permIds[]：提交勾选节点的 id（后端软删旧+重建新绑定）
  const permIds = treeRef.value.getCheckedKeys() as number[]
  permSaving.value = true
  try {
    await assignRolePermissionsApi(permRole.value.id, permIds)
    ElMessage.success('权限分配成功')
    permDialogVisible.value = false
  } catch {
    // 失败提示由 http.ts 统一处理
  } finally {
    permSaving.value = false
  }
}

onMounted(() => {
  void loadRoles()
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

.perm-tree {
  max-height: 420px;
  overflow-y: auto;
}
</style>
