package org.urbansafe.priority.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 应用统一时间源配置。
 */
@Configuration
public class TimeConfig {

    /**
     * 提供 UTC 系统时钟；测试可以使用固定 Clock 覆盖该 Bean。
     *
     * @return UTC 系统时钟
     */
    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}
