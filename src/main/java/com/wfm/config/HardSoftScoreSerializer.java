package com.wfm.config;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class HardSoftScoreSerializer extends JsonSerializer<HardSoftScore> {

    @Override
    public void serialize(HardSoftScore value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        gen.writeStartObject();
        gen.writeNumberField("hardScore", value.hardScore());
        gen.writeNumberField("softScore", value.softScore());
        gen.writeEndObject();
    }
}
