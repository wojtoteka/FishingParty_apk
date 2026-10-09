package wojtoteka.ovh.fishingparty.data

enum class ItemKind { BOAT, SAIL, SAILOR, POWERUP }

class ShopItem(val kind: ItemKind, val id: String, val price: Int) {
    val key: String get() = "${kind.name.lowercase()}:$id"
}

enum class PowerUp(val id: String, val price: Int) {
    MAGNET("magnet", 60),
    SHIELD("shield", 80),
    TURBO("turbo", 50),
    LIFE("life", 120),
}

object Catalog {
    val boats = listOf(
        ShopItem(ItemKind.BOAT, "classic", 0),
        ShopItem(ItemKind.BOAT, "crimson", 400),
        ShopItem(ItemKind.BOAT, "navy", 400),
        ShopItem(ItemKind.BOAT, "emerald", 600),
        ShopItem(ItemKind.BOAT, "ivory", 800),
        ShopItem(ItemKind.BOAT, "royal", 1200),
        ShopItem(ItemKind.BOAT, "shadow", 2000),
        ShopItem(ItemKind.BOAT, "golden", 3500),
    )
    val sails = listOf(
        ShopItem(ItemKind.SAIL, "white", 0),
        ShopItem(ItemKind.SAIL, "red", 200),
        ShopItem(ItemKind.SAIL, "blue", 200),
        ShopItem(ItemKind.SAIL, "yellow", 300),
        ShopItem(ItemKind.SAIL, "green", 300),
        ShopItem(ItemKind.SAIL, "black", 600),
        ShopItem(ItemKind.SAIL, "striped", 900),
        ShopItem(ItemKind.SAIL, "rainbow", 1500),
    )
    val sailors = listOf(
        ShopItem(ItemKind.SAILOR, "ginger", 0),
        ShopItem(ItemKind.SAILOR, "blond", 250),
        ShopItem(ItemKind.SAILOR, "brunet", 250),
        ShopItem(ItemKind.SAILOR, "diver", 500),
        ShopItem(ItemKind.SAILOR, "punk", 800),
        ShopItem(ItemKind.SAILOR, "captain", 1500),
    )
    val powerups = PowerUp.entries.map { ShopItem(ItemKind.POWERUP, it.id, it.price) }

    val cosmetics: List<ShopItem> get() = boats + sails + sailors

    val defaults = setOf("boat:classic", "sail:white", "sailor:ginger")

    fun find(key: String): ShopItem? = (cosmetics + powerups).firstOrNull { it.key == key }

    /** Koszt kontynuacji po porażce rośnie z każdym użyciem w danym podejściu. */
    fun reviveCost(continuesUsed: Int): Int = 100 * (continuesUsed + 1)
}
