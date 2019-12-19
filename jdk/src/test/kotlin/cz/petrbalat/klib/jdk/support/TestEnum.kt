package cz.petrbalat.klib.jdk.support

import cz.petrbalat.klib.jdk.Describe

/**
 * Created by balat on 24.9.2015.
 */
enum class TestEnum constructor(override val text: String) : Describe {

    PRVEK1("Prvek 1"),
    PRVEK2("Prvek 2")
}
