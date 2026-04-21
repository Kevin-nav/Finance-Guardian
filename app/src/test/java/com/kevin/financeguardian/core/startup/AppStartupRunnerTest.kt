package com.kevin.financeguardian.core.startup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kevin.financeguardian.data.local.DefaultCategorySeeder
import com.kevin.financeguardian.data.local.FinanceGuardianDatabase
import com.kevin.financeguardian.domain.model.DefaultCategories
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppStartupRunnerTest {
    private lateinit var database: FinanceGuardianDatabase
    private lateinit var runner: AppStartupRunner

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FinanceGuardianDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        runner = AppStartupRunner(DefaultCategorySeeder(database.categoryDao()))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun runStartupTasksSeedsDefaultCategories() = runTest {
        runner.runStartupTasks()

        val categoryIds = database.categoryDao().getAllOnce().map { it.id }.toSet()

        assertEquals(DefaultCategories.values.map { it.id }.toSet(), categoryIds)
    }

    @Test
    fun runningStartupTasksTwiceDoesNotDuplicateCategories() = runTest {
        runner.runStartupTasks()
        runner.runStartupTasks()

        val categories = database.categoryDao().getAllOnce()

        assertEquals(DefaultCategories.values.size, categories.size)
    }
}
