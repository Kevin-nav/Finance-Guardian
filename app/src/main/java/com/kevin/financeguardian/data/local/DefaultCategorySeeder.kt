package com.kevin.financeguardian.data.local

import com.kevin.financeguardian.data.local.dao.CategoryDao
import com.kevin.financeguardian.data.local.entity.CategoryEntity
import com.kevin.financeguardian.domain.model.DefaultCategories
import java.time.Instant
import javax.inject.Inject

class DefaultCategorySeeder @Inject constructor(
    private val categoryDao: CategoryDao,
) {
    suspend fun seedIfEmpty() {
        if (categoryDao.getAllOnce().isNotEmpty()) return

        val now = Instant.now()
        categoryDao.upsertAll(
            DefaultCategories.values.map { category ->
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
