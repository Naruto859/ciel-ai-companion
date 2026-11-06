package net.kuafuai.andee.net

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HubToolAccessTest {
    @Test fun `private tool names cannot be invoked by guessing them on the hub socket`() {
        val tools = ToolSchemas.all()
        for (i in 0 until tools.length()) {
            val tool = tools.getJSONObject(i)
            val name = tool.getString("name")
            if (tool.optBoolean("localOnly")) assertNull(name, ToolSchemas.hubMethodOf(name))
            else assertEquals(name, tool.getString("method"), ToolSchemas.hubMethodOf(name))
        }
        assertNull(ToolSchemas.hubMethodOf("note.recall"))
        assertNull(ToolSchemas.hubMethodOf("unknown_tool"))
    }

    @Test fun `an extension cannot reintroduce private tools using their internal method names`() {
        for (name in listOf("recall", "note.recall", "device.vault.fill", "get_screen_element", "debug.say")) {
            try {
                ToolSchemas.requireExtensionTools(JSONArray().put(JSONObject().put("name", name)))
                fail("reserved extension name accepted: $name")
            } catch (_: IllegalArgumentException) { }
        }
        ToolSchemas.requireExtensionTools(JSONArray().put(JSONObject().put("name", "pickup_call")))
    }
}
