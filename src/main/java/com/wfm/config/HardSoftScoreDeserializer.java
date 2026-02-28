package com.wfm.config;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class HardSoftScoreDeserializer extends JsonDeserializer<HardSoftScore> {

    @Override
    public HardSoftScore deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        int hardScore = node.get("hardScore").asInt();
        int softScore = node.get("softScore").asInt();
        return HardSoftScore.of(hardScore, softScore);
    }
}
