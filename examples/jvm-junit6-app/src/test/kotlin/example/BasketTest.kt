package example

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class BasketTest {

    @Test
    fun `sums the items`() {
        assertEquals(300, Basket.total(listOf(Item(price = 100, quantity = 3))))
    }

    @Test
    fun `applies the discount to a large order`() {
        assertEquals(5400, Basket.total(listOf(Item(price = 6000, quantity = 1))))
    }

    // Nothing here pins down the threshold itself, so the mutant that turns
    // `>` into `>=` survives. That is the point of the example.
    @Test
    fun `grants free shipping well above the threshold`() {
        assertTrue(Basket.isFreeShipping(9000))
    }
}
