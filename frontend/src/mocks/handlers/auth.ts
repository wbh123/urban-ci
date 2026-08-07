import { http } from 'msw'
import { okResponse, errorResponse, requireAuth, scenarioOf } from './helpers'
import { demoUser, DEMO_USERNAME, DEMO_PASSWORD, MOCK_TOKEN } from '../fixtures/data'

type MockCurrentUser = typeof demoUser

interface MockAccount {
  password: string
  token: string
  user: MockCurrentUser
}

function userWithRole(
  username: string,
  realName: string,
  roleCode: string,
  roleName: string,
  suffix: string,
): MockCurrentUser {
  return {
    ...demoUser,
    id: `00000000-0000-0000-0000-${suffix}`,
    username,
    realName,
    email: `${username}@example.com`,
    roles: [
      {
        id: `00000000-0000-0000-0001-${suffix}`,
        roleCode,
        roleName,
      },
    ],
  }
}

const accounts = new Map<string, MockAccount>([
  [DEMO_USERNAME, { password: DEMO_PASSWORD, token: MOCK_TOKEN, user: demoUser }],
  [
    'inspector',
    {
      password: 'demo123',
      token: 'mock-access-token:inspector',
      user: userWithRole('inspector', '现场巡检员', 'PROPERTY_INSPECTOR', '物业巡检人员', '000000000002'),
    },
  ],
  [
    'disposer',
    {
      password: 'demo123',
      token: 'mock-access-token:disposer',
      user: userWithRole('disposer', '问题处置员', 'DISPOSAL_OPERATOR', '问题处置人员', '000000000003'),
    },
  ],
  [
    'expert',
    {
      password: 'demo123',
      token: 'mock-access-token:expert',
      user: userWithRole('expert', '专业复核员', 'EXPERT', '专业复核人员', '000000000004'),
    },
  ],
  [
    'manager',
    {
      password: 'demo123',
      token: 'mock-access-token:manager',
      user: userWithRole('manager', '社区管理员', 'COMMUNITY_MANAGER', '街道社区管理人员', '000000000005'),
    },
  ],
  [
    'government',
    {
      password: 'demo123',
      token: 'mock-access-token:government',
      user: userWithRole('government', '住建管理人员', 'GOVERNMENT_MANAGER', '住建部门管理人员', '000000000006'),
    },
  ],
])

function tokenOf(request: Request): string {
  const authorization = request.headers.get('authorization') ?? ''
  return authorization.startsWith('Bearer ') ? authorization.slice(7).trim() : ''
}

function accountByToken(token: string): MockAccount | undefined {
  return [...accounts.values()].find((account) => account.token === token)
}

export const authHandlers = [
  http.post('/api/v1/auth/login', async ({ request }) => {
    const body = (await request.json().catch(() => ({}))) as {
      username?: string
      password?: string
    }
    const { username, password } = body
    if (!username || !password) {
      return errorResponse('BAD_REQUEST', '用户名和密码不能为空。', 400, [
        { field: !username ? 'username' : 'password', message: '不能为空' },
      ])
    }
    if (username === 'locked') {
      return errorResponse('ACCOUNT_LOCKED', '账号已停用或锁定。', 403)
    }
    const account = accounts.get(username)
    if (!account || account.password !== password) {
      return errorResponse('BAD_CREDENTIALS', '用户名或密码错误。', 401)
    }
    return okResponse({
      accessToken: account.token,
      tokenType: 'Bearer',
      expiresInSeconds: 7200,
      user: {
        id: account.user.id,
        username: account.user.username,
        realName: account.user.realName,
        roles: account.user.roles.map((role) => role.roleCode),
      },
    })
  }),

  http.post('/api/v1/auth/logout', ({ request }) => {
    const unauthenticated = requireAuth(request)
    if (unauthenticated) return unauthenticated
    return okResponse({ message: '退出成功' })
  }),

  http.get('/api/v1/auth/me', ({ request }) => {
    const unauthenticated = requireAuth(request)
    if (unauthenticated) return unauthenticated
    const scenario = scenarioOf(request)
    if (scenario === 'forbidden') {
      return errorResponse('ACCOUNT_LOCKED', '账号已停用或锁定。', 403)
    }
    if (scenario === 'not-found') {
      return errorResponse('USER_NOT_FOUND', '用户不存在。', 404)
    }
    const account = accountByToken(tokenOf(request))
    if (!account) return errorResponse('UNAUTHORIZED', '登录已过期。', 401)
    return okResponse(account.user)
  }),
]
