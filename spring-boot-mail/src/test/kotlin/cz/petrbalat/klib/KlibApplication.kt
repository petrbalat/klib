package cz.petrbalat.klib

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KlibMailApplication

fun main(args: Array<String>) {
	runApplication<KlibMailApplication>(*args)
}
