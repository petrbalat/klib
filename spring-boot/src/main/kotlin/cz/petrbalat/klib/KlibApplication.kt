package cz.petrbalat.klib

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KlibApplication

fun main(args: Array<String>) {
	runApplication<KlibApplication>(*args)
}
