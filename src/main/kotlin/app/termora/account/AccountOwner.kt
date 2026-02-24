package app.termora.account

import app.termora.database.OwnerType

data class AccountOwner(val id: String, val name: String, val type: OwnerType, val role: String) {
    fun isVisitorMode(): Boolean {
        return type == OwnerType.Team && role == TeamRole.Visitor.name
    }
}