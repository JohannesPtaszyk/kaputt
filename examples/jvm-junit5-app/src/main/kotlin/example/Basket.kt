package example

data class Item(val price: Int, val quantity: Int)

/** Order total in cents, with a discount over a threshold. */
object Basket {

    const val DISCOUNT_THRESHOLD = 5000
    const val DISCOUNT_PERCENT = 10

    fun total(items: List<Item>): Int {
        val subtotal = items.sumOf { it.price * it.quantity }
        return if (subtotal > DISCOUNT_THRESHOLD) {
            subtotal - subtotal * DISCOUNT_PERCENT / 100
        } else {
            subtotal
        }
    }

    fun isFreeShipping(total: Int): Boolean = total >= DISCOUNT_THRESHOLD
}
