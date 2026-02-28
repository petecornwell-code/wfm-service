package com.wfm.config;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Module hardSoftScoreModule() {
        SimpleModule module = new SimpleModule("HardSoftScoreModule");
        module.addSerializer(HardSoftScore.class, new HardSoftScoreSerializer());
        module.addDeserializer(HardSoftScore.class, new HardSoftScoreDeserializer());
        return module;
    }
}
