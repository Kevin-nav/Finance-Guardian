package com.kevin.financeguardian.data.local

import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.dao.CategoryDao
import com.kevin.financeguardian.data.local.entity.CategoryEntity
import com.kevin.financeguardian.domain.model.DefaultCategories
import javax.inject.Inject

class DefaultCategorySeeder @Inject constructor(
    private val categoryDao: CategoryDao,
    private val clock: AppClock,
) {
    suspend fun seedIfEmpty() {
        val existing = categoryDao.getAllOnce()
        val existingIds = existing.map { it.id }.toSet()
        val existingNames = existing.map { it.name.lowercase() }.toSet()

        val missingDefaults = DefaultCategories.values.filter { category ->
            category.id !in existingIds && category.name.lowercase() !in existingNames
        }
        if (missingDefaults.isEmpty()) return

        val now = clock.now()
        categoryDao.upsertAll(
            missingDefaults.map { category ->
                CategoryEntity(
                    id = category.id,
                    name = category.name,
                    type = category.type,
                    isArchived = false,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
    }
}
