package org.urbansafe.priority.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.phase2.repository.Phase2Repository;

/** 楼栋中心点定位必须在服务层执行对象范围权限、存在性与输入校验。 */
class ArchiveLocationServiceTest {

    @Test
    void getBuildingLocationChecksReadScopeAndReturnsStoredLocation() throws Exception {
        UUID buildingId = UUID.randomUUID();
        BusinessAccessService access = mock(BusinessAccessService.class);
        Phase2Repository repository = repositoryAnswer(invocation -> {
            if ("buildingExists".equals(invocation.method())) return true;
            if ("findBuildingLocation".equals(invocation.method())) {
                return Optional.of(Map.of("buildingId", buildingId, "provider", "MANUAL"));
            }
            return null;
        });
        Object service = newService(access, repository);

        Object result = method(service, "getBuildingLocation", UUID.class).invoke(service, buildingId);

        assertThat(result).isEqualTo(Map.of("buildingId", buildingId, "provider", "MANUAL"));
        verify(access).assertCanReadBuilding(buildingId);
    }

    @Test
    void missingBuildingLocationUsesStableNotFoundCode() throws Exception {
        UUID buildingId = UUID.randomUUID();
        BusinessAccessService access = mock(BusinessAccessService.class);
        Phase2Repository repository = repositoryAnswer(invocation -> {
            if ("buildingExists".equals(invocation.method())) return true;
            return "findBuildingLocation".equals(invocation.method()) ? Optional.empty() : null;
        });
        Object service = newService(access, repository);

        assertThatThrownBy(() -> invoke(service, "getBuildingLocation", buildingId))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("BUILDING_LOCATION_NOT_FOUND"));
        verify(access).assertCanReadBuilding(buildingId);
    }

    @Test
    void missingBuildingIsRejectedBeforeScopeAndLocationPersistence() throws Exception {
        UUID buildingId = UUID.randomUUID();
        BusinessAccessService access = mock(BusinessAccessService.class);
        AtomicReference<String> unexpectedRepositoryMethod = new AtomicReference<>();
        Phase2Repository repository = repositoryAnswer(invocation -> {
            if ("buildingExists".equals(invocation.method())) return false;
            unexpectedRepositoryMethod.set(invocation.method());
            return null;
        });
        Object service = newService(access, repository);

        assertThatThrownBy(() -> invoke(service, "getBuildingLocation", buildingId))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("BUILDING_NOT_FOUND"));
        assertThatThrownBy(() -> invokeSave(service, buildingId, Map.of(
                "longitude", 113.12,
                "latitude", 27.88,
                "provider", "MANUAL")))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("BUILDING_NOT_FOUND"));

        verifyNoInteractions(access);
        assertThat(unexpectedRepositoryMethod.get()).isNull();
    }

    @Test
    void saveBuildingLocationChecksManageScopeAndPersistsMockMetadata() throws Exception {
        UUID buildingId = UUID.randomUUID();
        BusinessAccessService access = mock(BusinessAccessService.class);
        AtomicReference<List<Object>> savedArguments = new AtomicReference<>();
        Phase2Repository repository = repositoryAnswer(invocation -> {
            if ("buildingExists".equals(invocation.method())) return true;
            if ("json".equals(invocation.method())) {
                return "{\"sourceMode\":\"MAP_CLICK\",\"mock\":true}";
            }
            if ("saveBuildingLocation".equals(invocation.method())) {
                savedArguments.set(invocation.arguments());
                return Map.of("buildingId", buildingId, "provider", invocation.arguments().get(4));
            }
            return null;
        });
        Object service = newService(access, repository);

        Object result = method(service, "saveBuildingLocation", UUID.class, Map.class).invoke(
                service,
                buildingId,
                Map.of(
                        "longitude", 113.12,
                        "latitude", 27.88,
                        "formattedAddress", "示范路1号",
                        "provider", "MOCK",
                        "matchLevel", "MOCK_PREVIEW",
                        "mock", true,
                        "metadata", Map.of("sourceMode", "MAP_CLICK")));

        assertThat(result).isEqualTo(Map.of("buildingId", buildingId, "provider", "MOCK"));
        verify(access).assertCanManageBuilding(buildingId);
        assertThat(savedArguments.get())
                .containsExactly(
                        buildingId,
                        113.12,
                        27.88,
                        "示范路1号",
                        "MOCK",
                        "MOCK_PREVIEW",
                        "{\"sourceMode\":\"MAP_CLICK\",\"mock\":true}");
    }

    @Test
    void invalidCoordinatesAndProviderAreRejectedBeforePersistence() throws Exception {
        UUID buildingId = UUID.randomUUID();
        BusinessAccessService access = mock(BusinessAccessService.class);
        AtomicReference<String> repositoryMethod = new AtomicReference<>();
        Phase2Repository repository = repositoryAnswer(invocation -> {
            repositoryMethod.set(invocation.method());
            return null;
        });
        Object service = newService(access, repository);

        assertThatThrownBy(() -> invokeSave(service, buildingId, Map.of(
                "longitude", 181,
                "latitude", 27.88,
                "provider", "MANUAL")))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("MAP_COORDINATE_INVALID"));
        assertThat(repositoryMethod.get()).isNull();

        assertThatThrownBy(() -> invokeSave(service, buildingId, Map.of(
                "longitude", 113.12,
                "latitude", 27.88,
                "provider", "UNKNOWN")))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("MAP_PROVIDER_INVALID"));
        assertThat(repositoryMethod.get()).isNull();
    }

    private Object newService(BusinessAccessService access, Phase2Repository repository) throws Exception {
        Class<?> type = load("org.urbansafe.priority.map.service.ArchiveLocationService");
        return type.getConstructor(BusinessAccessService.class, Phase2Repository.class)
                .newInstance(access, repository);
    }

    private Method method(Object service, String name, Class<?>... parameterTypes) throws Exception {
        return service.getClass().getMethod(name, parameterTypes);
    }

    private void invokeSave(Object service, UUID buildingId, Map<String, Object> body) throws Throwable {
        invoke(service, "saveBuildingLocation", buildingId, body);
    }

    private void invoke(Object service, String name, Object... args) throws Throwable {
        try {
            Class<?>[] parameterTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                parameterTypes[i] = args[i] instanceof UUID ? UUID.class : Map.class;
            }
            method(service, name, parameterTypes).invoke(service, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    private Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            assertThat(ex).as("%s 应存在", className).isNull();
            throw new AssertionError("unreachable", ex);
        }
    }

    private Phase2Repository repositoryAnswer(RepositoryAnswer answer) {
        return mock(Phase2Repository.class, invocation -> answer.answer(new Invocation(
                invocation.getMethod().getName(), List.of(invocation.getArguments()))));
    }

    @FunctionalInterface
    private interface RepositoryAnswer {
        Object answer(Invocation invocation) throws Throwable;
    }

    private record Invocation(String method, List<Object> arguments) {
    }
}
