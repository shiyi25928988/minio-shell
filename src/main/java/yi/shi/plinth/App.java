package yi.shi.plinth;

import lombok.extern.slf4j.Slf4j;
import yi.shi.plinth.annotation.PropertiesFile;
import yi.shi.plinth.boot.ServiceBooter;
import yi.shi.plinth.modules.DataSourceModule;


/**
 * Hello world!
 *
 */
@PropertiesFile(files = { "application.properties" })
@Slf4j
public class App
{
    /** 应用启动时间戳，用作静态资源版本号（?v=），重启后浏览器必然重新加载最新 JS/CSS，避免缓存旧版 */
    public static final long START_TIME = System.currentTimeMillis();

    public static void main( String[] args ) {
        try {
            log.info("启动应用");
            ServiceBooter.startFrom(App.class, new DataSourceModule());
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}
