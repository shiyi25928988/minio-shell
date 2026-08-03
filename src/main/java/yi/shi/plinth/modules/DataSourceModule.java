package yi.shi.plinth.modules;

import com.google.inject.name.Names;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.mybatis.guice.MyBatisModule;
import org.mybatis.guice.datasource.builtin.PooledDataSourceProvider;
import yi.shi.plinth.reflection.ClassHelper;
import yi.shi.plinth.reflection.ClassUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * MyBatis-Guice 数据库模块。
 *
 * <p>数据源通过 {@link PooledDataSourceProvider} + {@link JdbcTransactionFactory} 配置，
 * 读取系统属性 {@code JDBC.driver / JDBC.url / JDBC.username / JDBC.password}；
 * 并自动扫描指定包下带 {@link Mapper} 注解的接口注册为 Mapper，同时开启驼峰映射。
 *
 * <p>扫描包可通过构造器显式传入，或由系统属性 {@code mybatis.mapper.scan} 指定
 * （支持逗号分隔多个包），未配置时默认扫描 {@code yi.shi.db.mapper}。
 *
 * <p>扫描使用框架内置的 {@link ClassUtils}（与 {@code @HttpService} 扫描一致，覆盖 main 与 test-classes）。
 */
public class DataSourceModule extends MyBatisModule {

    private static final String DEFAULT_SCAN_PACKAGE = "yi.shi.plinth.db.mapper";
    private static final String SCAN_PROPERTY = "mybatis.mapper.scan";

    private final String[] scanPackages;

    /**
     * 使用系统属性 {@code mybatis.mapper.scan}（逗号分隔多包）配置扫描包，
     * 未配置时回退到默认包 {@code yi.shi.db.mapper}。
     */
    public DataSourceModule() {
        this(parsePackages(System.getProperty(SCAN_PROPERTY, DEFAULT_SCAN_PACKAGE)));
    }

    /**
     * @param scanPackages 待扫描的 Mapper 包，支持多个
     */
    public DataSourceModule(String... scanPackages) {
        this.scanPackages = (scanPackages == null || scanPackages.length == 0)
                ? new String[]{DEFAULT_SCAN_PACKAGE}
                : scanPackages;
    }

    @Override
    protected void initialize() {
        Names.bindProperties(binder(), System.getProperties());
        bindDataSourceProviderType(PooledDataSourceProvider.class);
        bindTransactionFactoryType(JdbcTransactionFactory.class);
        mapUnderscoreToCamelCase(true);
        getMapperClasses().forEach(this::addMapperClass);
    }

    private Set<Class<?>> getMapperClasses() {
        Set<Class<?>> classSet = new HashSet<>();
        for (String pkg : scanPackages) {
            try {
                classSet.addAll(ClassUtils.getClassSet(pkg));
            } catch (IOException e) {
                throw new RuntimeException("Failed to scan mapper package: " + pkg, e);
            }
        }
        return ClassHelper.pickClassByAnnotation(classSet, Mapper.class);
    }

    private static String[] parsePackages(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }
}
