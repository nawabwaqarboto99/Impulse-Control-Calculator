package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Impulse Calculator", appName)
  }

  @Test
  fun `check math hourly cost conversion`() {
    val wage = 20.0
    val price1 = 120.0
    val price2 = 50.0
    
    assertEquals(6.0, price1 / wage, 0.001)
    assertEquals(2.5, price2 / wage, 0.001)
  }

  @Test
  fun `check item name and price parser overrides`() {
    val result1 = parseInput("mechanical keyboard 150")
    assertEquals(150.0, result1.price)
    assertEquals("Mechanical Keyboard", result1.name)

    val result2 = parseInput("$49.99 premium subscriptions")
    assertEquals(49.99, result2.price)
    assertEquals("Premium Subscriptions", result2.name)

    val result3 = parseInput("12")
    assertEquals(12.0, result3.price)
    assertEquals(null, result3.name)
  }

  @Test
  fun `check mathematical cost engine representation fallback`() {
    val resultText = generateMathematicalFallback(
      wage = 20.0,
      itemPrice = 120.0,
      goalName = "Japan Trip",
      goalCost = 1000.0
    )
    val expected = "⚠️ This item costs **6 hours** of your life (\$120.00).\n🎯 Your goal (Japan Trip) requires **50 total hours** of work (\$1000.00).\n📉 Buying this item delays your target by adding **6 hours** more work toward your goal."
    assertEquals(expected, resultText)
  }
}
