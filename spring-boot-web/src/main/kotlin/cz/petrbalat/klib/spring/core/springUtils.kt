package cz.petrbalat.klib.spring.core

import org.springframework.core.io.ClassPathResource
import java.nio.file.Path

val classPath: Path
    get() = ClassPathResource(".").file.toPath()
