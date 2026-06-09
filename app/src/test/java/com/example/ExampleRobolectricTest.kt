package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    assertEquals("War Machine Hybrid", appName)
  }

  @Test
  fun `test OfflineKeywordMatcher matchKeywords`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val matcher = OfflineKeywordMatcher(context)
    matcher.init(context)
    val result = matcher.matchKeywords("laptop on a desk", context)
    assertNotNull(result)
    println("DEBUG TEST RESULT SIZE: ${result.size}")
    if (result.isNotEmpty()) {
      println("DEBUG TEST KWS: ${result.take(5)}")
    }
  }
}
