package app.termora.account

import app.termora.db.OwnerType

data class AccountOwner(val id: String, val name: String, val type: OwnerType) {
}