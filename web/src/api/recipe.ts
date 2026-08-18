import { request } from '../lib/http'
import type { PageResult } from './system'

export type RecipeVersionStatus = 'draft' | 'active' | 'obsolete'

export interface MesRecipeItem {
  id: number | string
  recipeCode: string
  recipeName: string
  enabled: number
  remark: string | null
  version: number
  activeVersionId: number | string | null
  activeVersionNo: number | null
  createTime: string | null
  updateTime: string | null
}

export interface MesRecipeVersionItem {
  id: number | string
  recipeId: number | string
  versionNo: number
  status: RecipeVersionStatus
  remark: string | null
  publishedAt: string | null
  createTime: string | null
  updateTime: string | null
}

export interface MesRecipeVersionDetail extends MesRecipeVersionItem {
  recipeCode: string
  recipeName: string
  bodyJson: string | null
  bodyObjectKey: string | null
}

export interface MesRecipeBindingItem {
  id: number | string
  stepId: number | string
  stepCode: string | null
  stepName: string | null
  eqpId: number | string | null
  eqpCode: string | null
  eqpType: string | null
  recipeId: number | string
  recipeCode: string | null
  recipeName: string | null
  recipeVersionId: number | string | null
  recipeVersionNo: number | null
  enabled: number
  createTime: string | null
  updateTime: string | null
}

export interface RecipeResolveResult {
  recipeId: number | string
  recipeCode: string
  recipeName: string
  versionId: number | string
  versionNo: number
  stepId: number | string
  eqpId: number | string
}

export function listRecipesApi(query: {
  keyword?: string
  enabled?: 0 | 1 | ''
  page?: number
  size?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.enabled === 0 || query.enabled === 1) params.set('enabled', String(query.enabled))
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<MesRecipeItem>>(`/recipes?${params}`, { method: 'GET' })
}

export function getRecipeApi(id: number | string) {
  return request<MesRecipeItem>(`/recipes/${id}`, { method: 'GET' })
}

export function createRecipeApi(body: {
  recipeCode: string
  recipeName: string
  remark?: string
}) {
  return request<MesRecipeItem>('/recipes', { method: 'POST', body })
}

export function updateRecipeApi(
  id: number | string,
  body: { recipeName: string; remark?: string },
) {
  return request<void>(`/recipes/${id}`, { method: 'PUT', body })
}

export function updateRecipeEnabledApi(id: number | string, enabled: 0 | 1) {
  return request<void>(`/recipes/${id}/enabled`, {
    method: 'PUT',
    body: { enabled },
  })
}

export function listRecipeVersionsApi(recipeId: number | string) {
  return request<MesRecipeVersionItem[]>(`/recipes/${recipeId}/versions`, { method: 'GET' })
}

export function getRecipeVersionApi(versionId: number | string) {
  return request<MesRecipeVersionDetail>(`/recipes/versions/${versionId}`, { method: 'GET' })
}

export function createRecipeDraftApi(
  recipeId: number | string,
  body: { fromVersionId?: number | string; remark?: string } = {},
) {
  return request<{ versionId: number | string }>(`/recipes/${recipeId}/versions`, {
    method: 'POST',
    body,
  })
}

export function updateRecipeDraftApi(
  versionId: number | string,
  body: { bodyJson?: string; bodyObjectKey?: string; remark?: string },
) {
  return request<void>(`/recipes/versions/${versionId}`, { method: 'PUT', body })
}

export function publishRecipeVersionApi(versionId: number | string) {
  return request<void>(`/recipes/versions/${versionId}/publish`, { method: 'POST' })
}

export function resolveRecipeApi(stepId: number | string, eqpId: number | string) {
  const params = new URLSearchParams()
  params.set('stepId', String(stepId))
  params.set('eqpId', String(eqpId))
  return request<RecipeResolveResult | null>(`/recipes/resolve?${params}`, { method: 'GET' })
}

export function listRecipeBindingsApi(query: {
  stepId?: number | string
  recipeId?: number | string
  eqpId?: number | string
  eqpType?: string
  enabled?: 0 | 1 | ''
  page?: number
  size?: number
} = {}) {
  const params = new URLSearchParams()
  if (query.stepId != null && query.stepId !== '') params.set('stepId', String(query.stepId))
  if (query.recipeId != null && query.recipeId !== '') params.set('recipeId', String(query.recipeId))
  if (query.eqpId != null && query.eqpId !== '') params.set('eqpId', String(query.eqpId))
  if (query.eqpType?.trim()) params.set('eqpType', query.eqpType.trim())
  if (query.enabled === 0 || query.enabled === 1) params.set('enabled', String(query.enabled))
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<MesRecipeBindingItem>>(`/recipe-bindings?${params}`, { method: 'GET' })
}

export function createRecipeBindingApi(body: {
  stepId: number | string
  eqpId?: number | string
  eqpType?: string
  recipeId: number | string
  recipeVersionId?: number | string
  enabled?: number
}) {
  return request<MesRecipeBindingItem>('/recipe-bindings', { method: 'POST', body })
}

export function deleteRecipeBindingApi(id: number | string) {
  return request<void>(`/recipe-bindings/${id}`, { method: 'DELETE' })
}
