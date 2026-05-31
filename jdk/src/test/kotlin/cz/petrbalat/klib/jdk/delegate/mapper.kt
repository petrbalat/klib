package cz.petrbalat.klib.jdk.delegate

import com.fasterxml.jackson.annotation.JsonAutoDetect
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

val mapper: JsonMapper = JsonMapper.builder()
        .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .changeDefaultVisibility { checker ->
            checker.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withCreatorVisibility(JsonAutoDetect.Visibility.NONE)
        }
//        .addModule(JavaTimeModule())
        .addModule(KotlinModule.Builder().build())
        .build()
