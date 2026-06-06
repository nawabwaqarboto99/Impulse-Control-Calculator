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
}
