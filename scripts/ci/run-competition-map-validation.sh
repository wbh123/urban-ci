#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -z "${ROOT_DIR}" ]]; then
  echo "错误：请在 urban-safe-priority Git 仓库内执行。" >&2
  exit 2
fi

REQUIRE_DOCKER_TESTS="${REQUIRE_DOCKER_TESTS:-0}"
DB_INTEGRATION_STATUS="NOT_RUN"

section() {
  echo
  echo "================================================================"
  echo "$1"
  echo "================================================================"
}

docker_ready() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1
}

print_docker_hint() {
  echo "当前后端 PostgreSQL/PostGIS 集成测试由 Testcontainers 启动数据库。"
  echo "检测到 Docker daemon 不可访问，因此不能把数据库集成测试标记为通过。"
  echo
  echo "WSL / Docker Desktop 环境请先确认："
  echo "  docker info"
  echo "能够正常返回 Server 信息后，再补跑严格门禁："
  echo "  REQUIRE_DOCKER_TESTS=1 bash scripts/ci/run-competition-map-validation.sh"
}

print_backend_failure_diagnostics() {
  local reports_dir="${ROOT_DIR}/backend-java/server/target/surefire-reports"

  section "后端失败诊断：Docker / Testcontainers"
  if docker_ready; then
    echo "[OK] Docker daemon 可访问。"
    docker version --format 'Client={{.Client.Version}} Server={{.Server.Version}}' 2>/dev/null || true
    echo
    echo "相关 PostgreSQL/PostGIS 容器："
    docker ps -a --format '{{.ID}}\t{{.Image}}\t{{.Status}}\t{{.Names}}' \
      | grep -Ei 'postgres|postgis|testcontainer|ryuk' || echo "未发现相关容器。"
  else
    echo "[ERROR] Docker daemon 当前不可访问。"
    print_docker_hint
  fi

  section "后端失败诊断：Surefire 首个根因"
  if [[ ! -d "${reports_dir}" ]]; then
    echo "未找到 ${reports_dir}，无法展开 Spring Boot 首个 Caused by。"
    return
  fi

  echo "Surefire 报告目录：${reports_dir}"
  echo
  echo "关键异常索引："
  grep -RInE \
    'Caused by:|BeanCreationException|UnsatisfiedDependencyException|ContainerLaunchException|Could not find a valid Docker environment|FlywayException|Migration.*failed|PSQLException|Connection refused|ExceptionInInitializerError|IllegalStateException' \
    "${reports_dir}" 2>/dev/null | head -n 160 || true

  local first_report
  first_report="$(find "${reports_dir}" -maxdepth 1 -type f \
    \( -name 'org.urbansafe.priority.building.BuildingPaginationIntegrationTest.txt' \
       -o -name 'org.urbansafe.priority.spatial.SpatialBoundaryRepositoryIntegrationTest.txt' \) \
    | sort | head -n 1)"

  if [[ -n "${first_report}" ]]; then
    echo
    echo "首个专项集成测试完整报告（前 320 行）：${first_report}"
    sed -n '1,320p' "${first_report}"
  fi
}

section "脚本：展示数据生成、缓存与状态覆盖契约"
bash "${ROOT_DIR}/scripts/ci/validate-showcase-generation-mode.sh"
grep -q 'geo.community_location' "${ROOT_DIR}/scripts/dev/generate-showcase-data.sh"
grep -q 'geo.building_location' "${ROOT_DIR}/scripts/dev/generate-showcase-data.sh"
grep -q 'showcaseSuppressed' "${ROOT_DIR}/scripts/dev/generate-showcase-data.sh"
grep -q '已经生成样例小区，但 geo.community_location 中没有可用小区位置' "${ROOT_DIR}/scripts/dev/generate-showcase-data.sh"
grep -q 'SHOWCASE_AMAP_CACHE_MODE' "${ROOT_DIR}/scripts/dev/generate-showcase-data.sh"
grep -q 'ensure-showcase-status-coverage.sql' "${ROOT_DIR}/scripts/dev/generate-showcase-data.sh"
echo "[OK] 城市样例数据、空间真实性、高德缓存与状态覆盖契约存在。"

section "前端：TypeScript + 驾驶舱/地图/原生状态标记专项 Vitest"
pushd "${ROOT_DIR}/frontend" >/dev/null
npm run type-check
npx vitest run \
  src/app/bootstrap-status-rings.test.ts \
  src/pages/console/ConsoleDashboardPage.test.ts \
  src/shared/map/spatial-boundary-editor.test.ts \
  src/shared/map/spatial-amap.test.ts \
  src/shared/map/spatial-amap-native-status-markers.test.ts \
  src/shared/map/spatial-amap-overview-presentation.test.ts \
  src/shared/map/spatial-amap-highlight-fallback.test.ts \
  src/shared/map/spatial-amap-community-marker-shape.test.ts \
  src/shared/map/spatial-amap-community-point-focus.test.ts \
  src/stores/spatial-map.test.ts \
  src/pages/console/ConsoleSpatialMapPage.test.ts \
  src/pages/console/ConsoleSpatialMapPage-native-status.test.ts \
  src/components/workbench/WorkbenchDataWallMap.presentation.test.ts \
  src/shared/navigation/console-menu.test.ts \
  src/shared/styles/__tests__/design-system.test.ts \
  src/shared/components/__tests__/GovernanceFlowHints.test.ts \
  src/mocks/archive-mock-flow.test.ts
popd >/dev/null

section "后端：编译 + 不依赖 Docker 的候选边界契约测试"
pushd "${ROOT_DIR}/backend-java" >/dev/null
mvn -B -ntp -pl server -am -DskipTests compile
mvn -B -ntp \
  -pl server -am \
  -Dtest=R41BoundaryCandidateContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
popd >/dev/null

if docker_ready; then
  section "后端：Docker 可用，执行楼栋排序 + PostgreSQL/PostGIS 空间集成测试"
  pushd "${ROOT_DIR}/backend-java" >/dev/null
  set +e
  mvn -B -ntp \
    -pl server -am \
    -Dtest=BuildingPaginationIntegrationTest,SpatialBoundaryRepositoryIntegrationTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    test
  backend_status=$?
  set -e
  popd >/dev/null

  if [[ ${backend_status} -ne 0 ]]; then
    DB_INTEGRATION_STATUS="FAILED"
    print_backend_failure_diagnostics
    echo
    echo "[FAIL] PostgreSQL/PostGIS 专项集成测试失败。" >&2
    exit "${backend_status}"
  fi
  DB_INTEGRATION_STATUS="PASSED"
else
  DB_INTEGRATION_STATUS="SKIPPED_NO_DOCKER"
  section "后端：数据库集成测试已跳过"
  print_docker_hint
  if [[ "${REQUIRE_DOCKER_TESTS}" == "1" ]]; then
    echo
    echo "[FAIL] REQUIRE_DOCKER_TESTS=1，但 Docker daemon 不可访问。" >&2
    exit 3
  fi
fi

if [[ "${RUN_E2E:-0}" == "1" ]]; then
  section "前端：Playwright 全量回归"
  pushd "${ROOT_DIR}/frontend" >/dev/null
  npm run test:e2e
  popd >/dev/null
fi

echo
if [[ "${DB_INTEGRATION_STATUS}" == "PASSED" ]]; then
  echo "[SUCCESS] 驾驶舱、地图、高德缓存与原生双层状态标记专项自动化验证完成，PostgreSQL/PostGIS 集成测试已通过。"
else
  echo "[PARTIAL SUCCESS] 展示数据缓存与地图专项快速门禁已通过；PostgreSQL/PostGIS 集成测试因 Docker 不可访问而跳过。"
  echo "这不会阻断当前开发，但不能视为数据库集成验收完成。"
fi
echo "人工检查：管理总览→建档→空间档案→巡检→移动取证→专业复核→地图→更新优先级；真实高德点位；楼栋内圆风险/外圈优先级；小区空心菱形；楼栋选中后高德 Buildings 高亮。"
