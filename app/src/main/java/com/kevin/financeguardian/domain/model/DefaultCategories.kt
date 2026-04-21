package com.kevin.financeguardian.domain.model

object DefaultCategories {
    val values = listOf(
        Category(id = "food", name = "Food", type = CategoryType.EXPENSE),
        Category(id = "transport", name = "Transport", type = CategoryType.EXPENSE),
        Category(id = "airtime_data", name = "Airtime/Data", type = CategoryType.EXPENSE),
        Category(id = "bills", name = "Bills", type = CategoryType.EXPENSE),
        Category(id = "subscriptions", name = "Subscriptions", type = CategoryType.EXPENSE),
        Category(id = "laundry", name = "Laundry", type = CategoryType.EXPENSE),
        Category(id = "family", name = "Family", type = CategoryType.EXPENSE),
        Category(id = "transfers", name = "Transfers", type = CategoryType.TRANSFER),
        Category(id = "income", name = "Income", type = CategoryType.INCOME),
        Category(id = "savings", name = "Savings", type = CategoryType.SAVINGS),
        Category(id = "unknown", name = "Unknown", type = CategoryType.EXPENSE),
    )
}
