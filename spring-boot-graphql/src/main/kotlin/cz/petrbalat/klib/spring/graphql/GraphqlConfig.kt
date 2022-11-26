package cz.petrbalat.klib.spring.graphql

import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.RuntimeWiring
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime


/**
 * přesunout do klib po release spring boot 2.7
 */
@AutoConfigureBefore(GraphQlAutoConfiguration::class)
@ConditionalOnClass(GraphQlAutoConfiguration::class)
@AutoConfiguration
class GraphqlConfig {

    /**
     * nastavení zpracování LocalDate a LocalDateTime
     */
    @Bean
    fun runtimeWiringConfigurer(): RuntimeWiringConfigurer = object : RuntimeWiringConfigurer {

        override fun configure(builder: RuntimeWiring.Builder) {
            val date = GraphQLScalarType.newScalar()
                .name("LocalDate")
                .coercing(object :
                    Coercing<LocalDate, String> {
                    override fun serialize(dataFetcherResult: Any): String = dataFetcherResult.toString()

                    override fun parseValue(input: Any): LocalDate = LocalDate.parse(input.toString())

                    override fun parseLiteral(input: Any): LocalDate = parseValue(input)
                })

            val localDateTime = GraphQLScalarType.newScalar()
                .name("LocalDateTime")
                .coercing(object :
                    Coercing<LocalDateTime, String> {
                    override fun serialize(dataFetcherResult: Any): String = dataFetcherResult.toString()

                    override fun parseValue(input: Any): LocalDateTime {
                        val zonedDateTime = ZonedDateTime.parse(input.toString())
                        val instant: ZonedDateTime = zonedDateTime.withZoneSameInstant(ZoneId.systemDefault())
                        return instant.toLocalDateTime()
                    }

                    override fun parseLiteral(input: Any): LocalDateTime = parseValue(input)
                })

            builder.scalar(date.build()).scalar(localDateTime.build())
        }

    }

}
