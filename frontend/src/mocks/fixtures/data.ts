import type {
  CommunityPoint,
  MapRuntimeConfig,
  InspectionTask,
  InspectionRecord,
} from '@/shared/api'

/** 演示数据依据后端 OpenAPI 响应示例构造，不发明后端不存在的字段。 */
const now = (): string => new Date().toISOString()

export interface MockBuilding {
  id: string
  communityId: string
  buildingCode: string
  buildingName: string
  constructionYear: number
  floorCount: number
  residentCount: number
  status: 'ACTIVE' | 'INACTIVE'
  createdAt: string
}

export const COMMUNITY_ID = '11111111-1111-1111-1111-111111111111'
export const BUILDING_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
export const TASK_ID = '22222222-2222-2222-2222-222222222222'
export const DEMO_USERNAME = 'admin'
export const DEMO_PASSWORD = 'urban_safe_admin_password'
export const MOCK_TOKEN = 'mock-access-token'

export const mapRuntimeConfig: MapRuntimeConfig = {
  enabled: false,
  mode: 'MOCK',
  provider: 'AMAP',
  jsApiKey: '',
  serviceHost: '/_AMapService',
  securityJsCodeExposed: false,
  defaultCenter: { longitude: 113.13396, latitude: 27.82767 },
  defaultZoom: 12,
}

export const initialCommunities: CommunityPoint[] = [
  {
    communityId: COMMUNITY_ID,
    communityName: '示范小区',
    address: '湖南省株洲市天元区示范路1号',
    formattedAddress: '湖南省株洲市天元区示范路1号',
    longitude: 113.13396,
    latitude: 27.82767,
    provider: 'MOCK',
    matchLevel: 'MOCK_PREVIEW',
  },
  {
    communityId: '55555555-5555-5555-5555-555555555555',
    communityName: '安居小区',
    address: '湖南省株洲市天元区建设路88号',
    formattedAddress: '湖南省株洲市天元区建设路88号',
    provider: 'MOCK',
    matchLevel: 'MOCK_PREVIEW',
  },
]

export const initialBuildings: MockBuilding[] = [
  {
    id: BUILDING_ID,
    communityId: COMMUNITY_ID,
    buildingCode: 'B-001',
    buildingName: '1号楼',
    constructionYear: 2000,
    floorCount: 6,
    residentCount: 24,
    status: 'ACTIVE',
    createdAt: now(),
  },
  {
    id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    communityId: COMMUNITY_ID,
    buildingCode: 'B-002',
    buildingName: '2号楼',
    constructionYear: 2005,
    floorCount: 5,
    residentCount: 18,
    status: 'ACTIVE',
    createdAt: now(),
  },
]

export const initialTasks: InspectionTask[] = [
  {
    taskId: TASK_ID,
    taskCode: 'IT-20260714-0001',
    buildingId: BUILDING_ID,
    buildingName: '1号楼',
    communityId: COMMUNITY_ID,
    inspectionType: 'ROUTINE',
    title: '现场安全巡检',
    status: 'PENDING',
    version: 0,
    createdAt: now(),
  },
]

export const initialRecords: InspectionRecord[] = []

export const demoUser = {
  id: '00000000-0000-0000-0000-000000000001',
  username: 'admin',
  realName: '开发管理员',
  phone: '138****0000',
  email: 'admin@example.com',
  organizationName: '城安智序演示组织',
  status: 'ACTIVE' as const,
  roles: [
    { id: '00000000-0000-0000-0000-000000000010', roleCode: 'ADMIN', roleName: '管理员' },
  ],
  createdAt: now(),
}

/** 可变内存数据库，便于工作台创建/流转任务与记录。 */
export interface MockDb {
  communities: CommunityPoint[]
  buildings: MockBuilding[]
  tasks: InspectionTask[]
  records: InspectionRecord[]
}

function createDb(): MockDb {
  return {
    communities: [...initialCommunities],
    buildings: [...initialBuildings],
    tasks: [...initialTasks],
    records: [...initialRecords],
  }
}

export const db: MockDb = createDb()

export function resetDb(): void {
  db.communities = [...initialCommunities]
  db.buildings = [...initialBuildings]
  db.tasks = [...initialTasks]
  db.records = [...initialRecords]
}