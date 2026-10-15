package wojtoteka.ovh.fishingparty.data

enum class ItemKind { BOAT, SAIL, SAILOR, POWERUP }

class ShopItem(val kind: ItemKind, val id: String, val price: Int) {
    val key: String get() = "${kind.name.lowercase()}:$id"
}

/** Kolejność jest częścią zapisu gry (liczniki po indeksie) – nowe wzmocnienia tylko na końcu. */
enum class PowerUp(val id: String, val price: Int) {
    MAGNET("magnet", 60),
    SHIELD("shield", 80),
    TURBO("turbo", 50),
    LIFE("life", 120),
    NET("net", 70),
    FROST("frost", 90),
    LURE("lure", 100),
    RHYTHM("rhythm", 80),
}

object Catalog {
    val boats = listOf(
        ShopItem(ItemKind.BOAT, "classic", 0),
        ShopItem(ItemKind.BOAT, "crimson", 400),
        ShopItem(ItemKind.BOAT, "navy", 400),
        ShopItem(ItemKind.BOAT, "sky", 450),
        ShopItem(ItemKind.BOAT, "teal", 500),
        ShopItem(ItemKind.BOAT, "mint", 550),
        ShopItem(ItemKind.BOAT, "emerald", 600),
        ShopItem(ItemKind.BOAT, "lime", 600),
        ShopItem(ItemKind.BOAT, "amber", 700),
        ShopItem(ItemKind.BOAT, "ivory", 800),
        ShopItem(ItemKind.BOAT, "rose", 900),
        ShopItem(ItemKind.BOAT, "lavender", 1100),
        ShopItem(ItemKind.BOAT, "royal", 1200),
        ShopItem(ItemKind.BOAT, "silver", 1600),
        ShopItem(ItemKind.BOAT, "shadow", 2000),
        ShopItem(ItemKind.BOAT, "golden", 3500),
    )

    /** Żagle-flagi – w sklepie na osobnej karcie (przed [sails], bo te z nich korzystają). */
    val flags = listOf(
        ShopItem(ItemKind.SAIL, "poland", 700),
        ShopItem(ItemKind.SAIL, "germany", 600),
        ShopItem(ItemKind.SAIL, "ukraine", 600),
        ShopItem(ItemKind.SAIL, "lithuania", 600),
        ShopItem(ItemKind.SAIL, "czechia", 700),
        ShopItem(ItemKind.SAIL, "netherlands", 600),
        ShopItem(ItemKind.SAIL, "austria", 600),
        ShopItem(ItemKind.SAIL, "hungary", 600),
        ShopItem(ItemKind.SAIL, "france", 600),
        ShopItem(ItemKind.SAIL, "italy", 600),
        ShopItem(ItemKind.SAIL, "spain", 600),
        ShopItem(ItemKind.SAIL, "ireland", 600),
        ShopItem(ItemKind.SAIL, "belgium", 600),
        ShopItem(ItemKind.SAIL, "sweden", 700),
        ShopItem(ItemKind.SAIL, "norway", 700),
        ShopItem(ItemKind.SAIL, "finland", 700),
        ShopItem(ItemKind.SAIL, "denmark", 700),
        ShopItem(ItemKind.SAIL, "switzerland", 700),
        ShopItem(ItemKind.SAIL, "portugal", 700),
        ShopItem(ItemKind.SAIL, "greece", 800),
        ShopItem(ItemKind.SAIL, "uk", 800),
        ShopItem(ItemKind.SAIL, "usa", 800),
        ShopItem(ItemKind.SAIL, "canada", 800),
        ShopItem(ItemKind.SAIL, "mexico", 800),
        ShopItem(ItemKind.SAIL, "brazil", 800),
        ShopItem(ItemKind.SAIL, "argentina", 800),
        ShopItem(ItemKind.SAIL, "japan", 800),
    )
    val sails = listOf(
        ShopItem(ItemKind.SAIL, "white", 0),
        ShopItem(ItemKind.SAIL, "red", 200),
        ShopItem(ItemKind.SAIL, "blue", 200),
        ShopItem(ItemKind.SAIL, "orange", 250),
        ShopItem(ItemKind.SAIL, "pink", 250),
        ShopItem(ItemKind.SAIL, "yellow", 300),
        ShopItem(ItemKind.SAIL, "green", 300),
        ShopItem(ItemKind.SAIL, "purple", 300),
        ShopItem(ItemKind.SAIL, "cyan", 300),
        ShopItem(ItemKind.SAIL, "marine", 500),
        ShopItem(ItemKind.SAIL, "black", 600),
        ShopItem(ItemKind.SAIL, "candy", 600),
        ShopItem(ItemKind.SAIL, "pirate", 800),
        ShopItem(ItemKind.SAIL, "striped", 900),
        ShopItem(ItemKind.SAIL, "ocean", 1000),
        ShopItem(ItemKind.SAIL, "sunset", 1200),
        ShopItem(ItemKind.SAIL, "rainbow", 1500),
        ShopItem(ItemKind.SAIL, "gold", 2000),
    ) + flags
    val sailors = listOf(
        ShopItem(ItemKind.SAILOR, "ginger", 0),
        ShopItem(ItemKind.SAILOR, "blond", 250),
        ShopItem(ItemKind.SAILOR, "brunet", 250),
        ShopItem(ItemKind.SAILOR, "fisher", 300),
        ShopItem(ItemKind.SAILOR, "surfer", 400),
        ShopItem(ItemKind.SAILOR, "beach", 450),
        ShopItem(ItemKind.SAILOR, "diver", 500),
        ShopItem(ItemKind.SAILOR, "pirate", 700),
        ShopItem(ItemKind.SAILOR, "punk", 800),
        ShopItem(ItemKind.SAILOR, "ninja", 900),
        ShopItem(ItemKind.SAILOR, "ghost", 1200),
        ShopItem(ItemKind.SAILOR, "captain", 1500),
        ShopItem(ItemKind.SAILOR, "king", 1800),
        ShopItem(ItemKind.SAILOR, "golden", 2500),
    )
    val powerups = PowerUp.entries.map { ShopItem(ItemKind.POWERUP, it.id, it.price) }

    val cosmetics: List<ShopItem> get() = boats + sails + sailors

    val defaults = setOf("boat:classic", "sail:white", "sailor:ginger")

    fun find(key: String): ShopItem? = (cosmetics + powerups).firstOrNull { it.key == key }

    /** Wygląd z zapisu, którego ta wersja gry nie zna (np. zapis z nowszej wersji), wraca do domyślnego. */
    fun knownOr(kind: ItemKind, id: String, fallback: String): String =
        if (find("${kind.name.lowercase()}:$id") != null) id else fallback

    /** Koszt kontynuacji po porażce rośnie z każdym użyciem w danym podejściu. */
    fun reviveCost(continuesUsed: Int): Int = 100 * (continuesUsed + 1)
}
