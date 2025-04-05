package cz.petrbalat.klib.spring.graphql

import graphql.schema.Coercing
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class LocalDateTimeCoarcing : Coercing<LocalDateTime, String> {
    override fun serialize(dataFetcherResult: Any): String = dataFetcherResult.toString()

    override fun parseValue(input: Any): LocalDateTime {
        val str = input.toString()
        if (str.endsWith("Z")) {
            val zonedDateTime = ZonedDateTime.parse(str)
            val instant: ZonedDateTime = zonedDateTime.withZoneSameInstant(ZoneId.systemDefault())
            return instant.toLocalDateTime()
        }

        return LocalDateTime.parse(str)
    }

    override fun parseLiteral(input: Any): LocalDateTime = parseValue(input)
}