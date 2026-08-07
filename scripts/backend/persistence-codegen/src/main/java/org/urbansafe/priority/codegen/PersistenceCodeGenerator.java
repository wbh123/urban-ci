package org.urbansafe.priority.codegen;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.flywaydb.core.Flyway;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 根据 Flyway 已迁移的 PostgreSQL 元数据生成 MyBatis-Plus Entity 与基础 Mapper。
 *
 * <p>工具只在显式 {@code entity-codegen} profile 下执行。普通 Maven 生命周期只编译
 * 本模块，不会读取环境变量、连接数据库或修改 persistence 模块。</p>
 */
public final class PersistenceCodeGenerator {

    /** Flyway 版本迁移文件名格式，捕获开头 V 与双下划线之间的版本部分。 */
    private static final Pattern VERSIONED_MIGRATION_FILE =
            Pattern.compile("^V([0-9]+(?:[._][0-9]+)*)__.+\\.sql$");

    /** 第一阶段允许生成的表，顺序固定以保证跨机器输出稳定。 */
    private static final List<TableDefinition> TABLES = List.of(
            new TableDefinition("core", "user_account", "UserAccountEntity"),
            new TableDefinition("core", "role", "RoleEntity"),
            new TableDefinition("core", "user_role", "UserRoleEntity"),
            new TableDefinition("core", "community", "CommunityEntity"),
            new TableDefinition("core", "building", "BuildingEntity"),
            new TableDefinition("core", "building_evidence", "BuildingEvidenceEntity"),
            new TableDefinition("audit", "operation_log", "OperationLogEntity"));

    /** 禁止实例化命令行工具。 */
    private PersistenceCodeGenerator() {
    }

    /**
     * 返回生成器批准表清单的唯一数量来源，Shell 脚本不得复制该数量。
     *
     * @return 当前受控表清单中的表数量
     */
    public static int tableCount() {
        return TABLES.size();
    }

    /**
     * 读取环境变量、验证 Flyway 状态并生成到指定目录。
     *
     * @param args 命令行参数，本工具不接受包含密码的参数
     * @throws Exception 数据库元数据读取或模板渲染失败
     */
    public static void main(String[] args) throws Exception {
        // --table-count 不能读取数据库或环境变量，供 Shell 脚本安全取得唯一数量来源。
        if (args.length == 1 && "--table-count".equals(args[0])) {
            System.out.println(tableCount());
            return;
        }
        // --migrate 仅用于漂移脚本创建的临时数据库，绝不隐式执行在普通生成流程中。
        if (args.length == 1 && "--migrate".equals(args[0])) {
            migrateTemporaryDatabase();
            return;
        }
        if (args.length != 0) {
            throw new IllegalArgumentException("不支持的持久层生成器参数：" + String.join(" ", args));
        }

        String url = requireEnvironment("URBAN_SAFE_CODEGEN_DB_URL");
        String username = requireEnvironment("URBAN_SAFE_CODEGEN_DB_USERNAME");
        String password = requireEnvironment("URBAN_SAFE_CODEGEN_DB_PASSWORD");
        Path outputRoot = Path.of(System.getProperty(
                "urban.safe.codegen.outputDir",
                "persistence/src/generated/java"));

        Configuration configuration = templateConfiguration();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertFlywayReady(connection, defaultMigrationDirectory());
            for (TableDefinition table : TABLES) {
                generateTable(configuration, connection, outputRoot, table);
            }
        }
    }

    /**
     * 在调用方显式创建的临时数据库执行仓库迁移。
     *
     * <p>该方法只由漂移脚本的 {@code --migrate} 命令调用；普通 Maven 生命周期不会执行它，
     * 因而不会在构建中连接数据库或改变任何数据库状态。</p>
     */
    private static void migrateTemporaryDatabase() {
        // 连接信息只从环境变量读取，避免把密码放入进程列表或 Maven 命令行。
        String url = requireEnvironment("URBAN_SAFE_CODEGEN_DB_URL");
        String username = requireEnvironment("URBAN_SAFE_CODEGEN_DB_USERNAME");
        String password = requireEnvironment("URBAN_SAFE_CODEGEN_DB_PASSWORD");
        Path migrationDirectory = defaultMigrationDirectory();

        // 文件系统路径与服务端迁移目录一致，确保漂移检查不依赖 classpath 打包结果。
        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("filesystem:" + migrationDirectory.toAbsolutePath())
                .load();
        flyway.migrate();
    }

    /**
     * 读取必需环境变量，缺失时在连接数据库前失败。
     *
     * @param name 环境变量名
     * @return 去除首尾空白后的值；密码值不会被打印
     */
    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少持久层代码生成环境变量：" + name);
        }
        return value.trim();
    }

    /**
     * 创建严格模式 Freemarker 配置。
     *
     * @return 模板配置
     */
    private static Configuration templateConfiguration() {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_34);
        configuration.setClassLoaderForTemplateLoading(
                PersistenceCodeGenerator.class.getClassLoader(), "templates");
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        return configuration;
    }

    /**
     * 确认 Flyway 历史表存在、没有失败迁移且至少包含一个成功版本。
     *
     * @param connection PostgreSQL 连接
     */
    static void assertFlywayReady(Connection connection, Path migrationDirectory) throws SQLException {
        String sql = """
                SELECT version, success
                FROM public.flyway_schema_history
                ORDER BY installed_rank
                """;
        String databaseLatestVersion = null;
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                // 任一失败记录都意味着结构并非 Flyway 完整成功状态，不能据此生成代码。
                if (!resultSet.getBoolean("success")) {
                    throw new IllegalStateException("Flyway 存在失败迁移，拒绝生成持久层代码");
                }
                // Flyway 的 Schema 创建记录没有版本号，因此只比较有版本号的成功迁移记录。
                String candidateVersion = resultSet.getString("version");
                if (candidateVersion != null && !candidateVersion.isBlank()
                        && (databaseLatestVersion == null
                        || compareMigrationVersions(candidateVersion, databaseLatestVersion) > 0)) {
                    databaseLatestVersion = candidateVersion;
                }
            }
            if (databaseLatestVersion == null) {
                throw new IllegalStateException("Flyway 尚未完整成功执行，拒绝生成持久层代码");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "无法读取 public.flyway_schema_history，请先执行全部 Flyway 迁移",
                    exception);
        }

        // 数据库与仓库迁移版本必须完全一致，任一方向漂移都会导致生成快照不可复现。
        String repositoryLatestVersion = repositoryLatestMigrationVersion(migrationDirectory);
        int versionComparison = compareMigrationVersions(
                databaseLatestVersion, repositoryLatestVersion);
        if (versionComparison != 0) {
            throw new IllegalStateException(
                    "Flyway 迁移版本不一致：数据库最高成功版本=" + databaseLatestVersion
                            + "，仓库最高版本=" + repositoryLatestVersion
                            + "，拒绝生成持久层代码");
        }
    }

    /**
     * 返回默认仓库迁移目录，并允许测试用系统属性替换路径。
     *
     * @return 要比较或执行的 Flyway SQL 迁移目录
     */
    private static Path defaultMigrationDirectory() {
        // 默认相对路径以 backend-java 为工作目录，与两个脚本的 cd 目标保持一致。
        String directory = System.getProperty(
                "urban.safe.codegen.migrationDir", "server/src/main/resources/db/migration");
        return Path.of(directory);
    }

    /**
     * 扫描仓库迁移目录并找出最高版本的 {@code V*__*.sql} 迁移文件。
     *
     * @param migrationDirectory 仓库中的 Flyway SQL 迁移目录
     * @return 最高迁移版本，不包含文件名前缀 V
     */
    private static String repositoryLatestMigrationVersion(Path migrationDirectory) {
        if (!Files.isDirectory(migrationDirectory)) {
            throw new IllegalStateException("Flyway 迁移目录不存在：" + migrationDirectory.toAbsolutePath());
        }

        String latestVersion = null;
        try (var migrationFiles = Files.list(migrationDirectory)) {
            for (Path migrationFile : migrationFiles.toList()) {
                // 仅接受 Flyway 版本化 SQL 迁移；重复、撤销或说明文件不能影响最高版本。
                Matcher matcher = VERSIONED_MIGRATION_FILE.matcher(migrationFile.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }
                String candidateVersion = matcher.group(1).replace('_', '.');
                if (latestVersion == null
                        || compareMigrationVersions(candidateVersion, latestVersion) > 0) {
                    latestVersion = candidateVersion;
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "无法扫描 Flyway 迁移目录：" + migrationDirectory.toAbsolutePath(), exception);
        }
        if (latestVersion == null) {
            throw new IllegalStateException(
                    "Flyway 迁移目录未找到版本化 SQL 迁移：" + migrationDirectory.toAbsolutePath());
        }
        return latestVersion;
    }

    /**
     * 按 Flyway 数字版本片段比较两个版本，例如 {@code 10} 大于 {@code 9}。
     *
     * @param leftVersion 左侧版本号
     * @param rightVersion 右侧版本号
     * @return 正数表示左侧较新，零表示相等，负数表示右侧较新
     */
    private static int compareMigrationVersions(String leftVersion, String rightVersion) {
        // Flyway 文件名中的下划线与点均表示版本片段分隔符，统一后按数字逐段比较。
        String[] leftParts = leftVersion.replace('_', '.').split("\\.");
        String[] rightParts = rightVersion.replace('_', '.').split("\\.");
        int partCount = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < partCount; index++) {
            // 较短版本缺失的尾部片段按零处理，保证 1 与 1.0 语义相同。
            int leftPart = index < leftParts.length ? parseVersionPart(leftParts[index], leftVersion) : 0;
            int rightPart = index < rightParts.length ? parseVersionPart(rightParts[index], rightVersion) : 0;
            int comparison = Integer.compare(leftPart, rightPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    /**
     * 解析单个 Flyway 版本数字片段，并将非法版本明确报告为生成前置条件错误。
     *
     * @param part 待解析的数字片段
     * @param completeVersion 用于错误消息的完整版本
     * @return 解析后的整数版本片段
     */
    private static int parseVersionPart(String part, String completeVersion) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("无法解析 Flyway 数字版本：" + completeVersion, exception);
        }
    }

    /**
     * 读取单表列元数据并渲染 Entity、Mapper 两个文件。
     */
    private static void generateTable(
            Configuration configuration,
            Connection connection,
            Path outputRoot,
            TableDefinition table) throws SQLException, IOException, TemplateException {
        List<ColumnDefinition> columns = readColumns(connection, table);
        if (columns.isEmpty()) {
            throw new IllegalStateException(
                    "数据库中不存在生成目标表：" + table.schemaName() + "." + table.tableName());
        }

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("schemaName", table.schemaName());
        model.put("tableName", table.tableName());
        model.put("className", table.className());
        model.put("mapperName", table.className().replace("Entity", "Mapper"));
        model.put("columns", columns);
        model.put("autoResultMap", columns.stream().anyMatch(ColumnDefinition::isJson));

        render(configuration.getTemplate("entity.java.ftl"), model,
                outputRoot.resolve("org/urbansafe/priority/persistence/entity/")
                        .resolve(table.className() + ".java"));
        render(configuration.getTemplate("mapper.java.ftl"), model,
                outputRoot.resolve("org/urbansafe/priority/persistence/mapper/")
                        .resolve(table.className().replace("Entity", "Mapper") + ".java"));
    }

    /**
     * 按 ordinal_position 读取 PostgreSQL 列定义。
     */
    private static List<ColumnDefinition> readColumns(
            Connection connection,
            TableDefinition table) throws SQLException {
        String sql = """
                SELECT column_name, data_type, udt_name
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """;
        List<ColumnDefinition> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table.schemaName());
            statement.setString(2, table.tableName());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String columnName = resultSet.getString("column_name");
                    String dataType = resultSet.getString("data_type");
                    String udtName = resultSet.getString("udt_name");
                    columns.add(column(table.schemaName(), table.tableName(), columnName, dataType, udtName));
                }
            }
        }
        return columns;
    }

    /**
     * 将 PostgreSQL 类型映射为项目固定 Java 类型与 MyBatis-Plus 注解标记。
     */
    static ColumnDefinition column(
            String schemaName,
            String tableName,
            String columnName,
            String dataType,
            String udtName) {
        String normalized = udtName.toLowerCase(Locale.ROOT);
        String javaType = switch (normalized) {
            case "uuid" -> "UUID";
            case "json", "jsonb" -> "JsonNode";
            case "timestamptz" -> "OffsetDateTime";
            case "timestamp" -> "LocalDateTime";
            case "date" -> "LocalDate";
            case "numeric" -> "BigDecimal";
            case "int2" -> "Short";
            case "int4" -> "Integer";
            case "int8" -> "Long";
            case "bool" -> "Boolean";
            // 文本、定长文本和 Inet 在当前表清单中按项目受控的 String 类型处理。
            case "varchar", "text", "bpchar", "inet" -> "String";
            default -> throw new IllegalArgumentException(
                    "不支持的 PostgreSQL 列类型：" + schemaName + "." + tableName + "." + columnName
                            + "，data_type=" + dataType + "，udt_name=" + udtName);
        };
        return new ColumnDefinition(
                columnName,
                snakeToCamel(columnName),
                javaType,
                columnName.equals("id"),
                columnName.equals("deleted_at"),
                columnName.equals("version"),
                normalized.equals("json") || normalized.equals("jsonb"),
                columnName.equals("created_at"),
                columnName.equals("updated_at"));
    }

    /**
     * 将 snake_case 数据库列名转换为 camelCase Java 属性名。
     */
    private static String snakeToCamel(String value) {
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char character : value.toCharArray()) {
            if (character == '_') {
                upperNext = true;
            } else if (upperNext) {
                result.append(Character.toUpperCase(character));
                upperNext = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    /**
     * 将模板结果写入生成目录；调用方必须传入临时根目录。
     */
    private static void render(Template template, Map<String, Object> model, Path target)
            throws IOException, TemplateException {
        Files.createDirectories(target.getParent());
        try (Writer writer = Files.newBufferedWriter(
                target,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            template.process(model, writer);
        }
    }

    /** 生成表固定定义。 */
    private record TableDefinition(String schemaName, String tableName, String className) {
    }

    /**
     * 传给 FreeMarker 的公开 JavaBean 列定义。
     *
     * <p>不要改为 private record：FreeMarker 严格对象包装器只保证识别公开 JavaBean
     * getter，而 private 嵌套 record 会导致模板读取布尔标记失败。</p>
     */
    public static final class ColumnDefinition {

        /** 数据库原始列名。 */
        private final String columnName;
        /** 驼峰风格 Java 属性名。 */
        private final String propertyName;
        /** 映射后的 Java 类型名称。 */
        private final String javaType;
        /** 是否为主键列。 */
        private final boolean id;
        /** 是否为逻辑删除时间列。 */
        private final boolean logicDelete;
        /** 是否为乐观锁版本列。 */
        private final boolean version;
        /** 是否为 JSON/JSONB 列。 */
        private final boolean json;
        /** 是否为创建时间列。 */
        private final boolean createdAt;
        /** 是否为更新时间列。 */
        private final boolean updatedAt;

        /**
         * 创建列元数据对象。
         *
         * @param columnName 数据库原始列名
         * @param propertyName Java 属性名
         * @param javaType Java 类型名称
         * @param id 是否为主键列
         * @param logicDelete 是否为逻辑删除列
         * @param version 是否为版本列
         * @param json 是否为 JSON 列
         * @param createdAt 是否为创建时间列
         * @param updatedAt 是否为更新时间列
         */
        public ColumnDefinition(
                String columnName,
                String propertyName,
                String javaType,
                boolean id,
                boolean logicDelete,
                boolean version,
                boolean json,
                boolean createdAt,
                boolean updatedAt) {
            this.columnName = columnName;
            this.propertyName = propertyName;
            this.javaType = javaType;
            this.id = id;
            this.logicDelete = logicDelete;
            this.version = version;
            this.json = json;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        /** @return 数据库原始列名。 */
        public String getColumnName() { return columnName; }
        /** @return Java 属性名。 */
        public String getPropertyName() { return propertyName; }
        /** @return Java 类型名称。 */
        public String getJavaType() { return javaType; }
        /** @return 是否为主键列。 */
        public boolean isId() { return id; }
        /** @return 是否为逻辑删除列。 */
        public boolean isLogicDelete() { return logicDelete; }
        /** @return 是否为版本列。 */
        public boolean isVersion() { return version; }
        /** @return 是否为 JSON 列。 */
        public boolean isJson() { return json; }
        /** @return 是否为创建时间列。 */
        public boolean isCreatedAt() { return createdAt; }
        /** @return 是否为更新时间列。 */
        public boolean isUpdatedAt() { return updatedAt; }
    }
}
