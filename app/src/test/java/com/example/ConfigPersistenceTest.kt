package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConfigPersistenceTest {

    @Test
    fun testSliderAutoSaveAndRestore() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm1 = MainViewModel(app)

        // Set custom slider limits
        vm1.setTitleCharLimit(125f)
        vm1.setDescCharLimit(175f)
        vm1.setKeywordsLimit(35f)

        // Verify saved in SharedPreferences directly
        val prefs = app.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        assertEquals(125f, prefs.getFloat("saved_title_limit", 0f), 0.01f)
        assertEquals(175f, prefs.getFloat("saved_desc_limit", 0f), 0.01f)
        assertEquals(35f, prefs.getFloat("saved_keywords_limit", 0f), 0.01f)

        // Instantiate new ViewModel to simulate app restart
        val vm2 = MainViewModel(app)
        assertEquals(125f, vm2.titleCharLimit.value, 0.01f)
        assertEquals(175f, vm2.descCharLimit.value, 0.01f)
        assertEquals(35f, vm2.keywordsLimit.value, 0.01f)
    }

    @Test
    fun testBlacklistAndPromptConceptSaveAndClear() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm1 = MainViewModel(app)

        // Save blacklist words
        vm1.setBlacklistWords("vector, illustration, 3d")
        vm1.saveBlacklistWordsPermanent()

        // Save prompt concept
        vm1.setPromptConcept("coffee cup on wooden desk")
        vm1.savePromptConceptPermanent()

        // Verify in SharedPreferences
        val prefs = app.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        assertEquals("vector, illustration, 3d", prefs.getString("saved_blacklist_words", null))
        assertEquals("coffee cup on wooden desk", prefs.getString("saved_prompt_concept", null))

        // Re-create ViewModel and verify restored
        val vm2 = MainViewModel(app)
        assertEquals("vector, illustration, 3d", vm2.blacklistWords.value)
        assertEquals("coffee cup on wooden desk", vm2.promptConcept.value)

        // Clear blacklist words
        vm2.clearBlacklistWordsPermanent()
        assertEquals("", vm2.blacklistWords.value)
        assertEquals(null, prefs.getString("saved_blacklist_words", null))

        // Clear prompt concept
        vm2.clearPromptConceptPermanent()
        assertEquals("", vm2.promptConcept.value)
        assertEquals(null, prefs.getString("saved_prompt_concept", null))
    }

    @Test
    fun testApiListInitiallyEmptyOnFreshInstall() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("WarMachinePrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val vm = MainViewModel(app)
        // Verify empty on fresh install
        assertEquals(emptyList<String>(), vm.apiKeysList.value)
        assertEquals(-1, vm.activeApiKeyIndex.value)
        assertEquals("", vm.geminiKey.value)

        // User manually inputs key in Api Database
        vm.addApiKey("AIzaSyTestKey123")
        assertEquals(listOf("AIzaSyTestKey123"), vm.apiKeysList.value)
        assertEquals(0, vm.activeApiKeyIndex.value)
        assertEquals("AIzaSyTestKey123", vm.geminiKey.value)

        // Adding a second key
        vm.addApiKey("AIzaSySecondKey456")
        assertEquals(listOf("AIzaSyTestKey123", "AIzaSySecondKey456"), vm.apiKeysList.value)
        assertEquals(1, vm.activeApiKeyIndex.value)
        assertEquals("AIzaSySecondKey456", vm.geminiKey.value)

        // Verify restored on new ViewModel instance
        val vmRestored = MainViewModel(app)
        assertEquals(listOf("AIzaSyTestKey123", "AIzaSySecondKey456"), vmRestored.apiKeysList.value)
        assertEquals(1, vmRestored.activeApiKeyIndex.value)
        assertEquals("AIzaSySecondKey456", vmRestored.geminiKey.value)

        // Clear list
        vmRestored.clearApiKeysList()
        assertEquals(emptyList<String>(), vmRestored.apiKeysList.value)
        assertEquals(-1, vmRestored.activeApiKeyIndex.value)
        assertEquals("", vmRestored.geminiKey.value)
    }
}
