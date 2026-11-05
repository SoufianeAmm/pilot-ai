import { describe, expect, it } from 'vitest'
import { assignableRoles, canChangeRole, canRemove, hasPermission } from './permissions'

// These mirror the backend RolePolicyTest: if the backend rules change, these must change with them.
describe('organization permissions', () => {
  it('gives each role its permissions', () => {
    expect(hasPermission('OWNER', 'ORG_DELETE')).toBe(true)
    expect(hasPermission('ADMIN', 'ORG_DELETE')).toBe(false)
    expect(hasPermission('ADMIN', 'MEMBER_INVITE')).toBe(true)
    expect(hasPermission('PROJECT_MANAGER', 'PROJECT_CREATE')).toBe(true)
    expect(hasPermission('PROJECT_MANAGER', 'MEMBER_INVITE')).toBe(false)
    expect(hasPermission('MEMBER', 'PROJECT_CREATE')).toBe(false)
  })

  it('lets an owner invite with any role but an admin never above admin', () => {
    expect(assignableRoles('OWNER', null)).toEqual(['OWNER', 'ADMIN', 'PROJECT_MANAGER', 'MEMBER'])
    expect(assignableRoles('ADMIN', null)).toEqual(['ADMIN', 'PROJECT_MANAGER', 'MEMBER'])
    expect(assignableRoles('MEMBER', null)).toEqual([])
  })

  it('never lets an admin change or remove an owner', () => {
    expect(canChangeRole('ADMIN', 'OWNER')).toBe(false)
    expect(canRemove('ADMIN', 'OWNER')).toBe(false)
    expect(canChangeRole('ADMIN', 'MEMBER')).toBe(true)
    expect(canRemove('ADMIN', 'ADMIN')).toBe(true)
  })

  it('lets an owner change and remove anyone', () => {
    expect(canChangeRole('OWNER', 'OWNER')).toBe(true)
    expect(canRemove('OWNER', 'OWNER')).toBe(true)
  })

  it('gives project managers and members no member management', () => {
    expect(canChangeRole('PROJECT_MANAGER', 'MEMBER')).toBe(false)
    expect(canRemove('PROJECT_MANAGER', 'MEMBER')).toBe(false)
    expect(canChangeRole('MEMBER', 'MEMBER')).toBe(false)
    expect(canRemove('MEMBER', 'MEMBER')).toBe(false)
  })
})
