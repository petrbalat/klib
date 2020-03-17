package cz.petrbalat.klib.jdk.delegate

import cz.petrbalat.klib.jdk.cz.petrbalat.klib.jdk.delegate.DbDto
import cz.petrbalat.klib.jdk.io.directory
import java.io.File
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Created by balat on 22.04.2017.
 */
class JsonFileDbDelegateTest {

    val directory = File.createTempFile("testdb", "json").directory
    
    var db: DbDto by jsonFileDbByJackson(directory, mapper)

    var cacheDb: DbDto by cacheJsonFileDbByJackson(directory, mapper, Duration.ofSeconds(10))

    var arrayDb: Array<DbDto> by jsonFileDbByJackson(directory, mapper)

    @Test
    fun testGetSetDto() {
        assertEquals(DbDto(), db)
        db = DbDto(param2 = 2)
        assertEquals(DbDto(param2 = 2), db)
        db = DbDto()
    }

    @Test
    fun testCacheGetSetDto() {
        assertEquals(DbDto(), cacheDb)
        assertEquals(DbDto(), cacheDb)
        cacheDb = DbDto(param2 = 2)
        assertEquals(DbDto(param2 = 2), cacheDb)
        cacheDb = DbDto()
    }


    @Test
    fun testGetSetArrayDto() {
        assertEquals(0, arrayDb.size)
        arrayDb = arrayOf(DbDto(param3 = listOf("aaa")))
        assertEquals(1, arrayDb.size)
        arrayDb = emptyArray()
    }

}
