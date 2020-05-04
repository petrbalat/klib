package cz.petrbalat.klib.jwt

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KlibJwtApplication

fun main(args: Array<String>) {
    runApplication<KlibJwtApplication>(*args)
}
