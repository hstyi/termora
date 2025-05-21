package app.termora.account

import kotlinx.serialization.Serializable

@Serializable
data class Subscription(
    val id: String,
    val plan: SubscriptionPlan,
    val startDate: Long,
    val endDate: Long,
)
