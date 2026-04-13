package com.unscientificjszhai.mcpshortcuts.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolInputSchemaTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test parse standard json schema`() {
        val jsonString = """
            {
              "type": "object",
              "properties": {
                "string_param": {
                  "type": "string",
                  "description": "A simple string parameter"
                },
                "number_param": {
                  "type": "number"
                }
              },
              "required": ["string_param"]
            }
        """.trimIndent()

        val schema = json.decodeFromString<ToolInputSchema>(jsonString)

        assertEquals("object", schema.typeString)
        assertEquals(2, schema.properties.size)
        assertEquals("string", schema.properties["string_param"]?.typeString)
        assertEquals("A simple string parameter", schema.properties["string_param"]?.descriptionString)
        assertEquals("number", schema.properties["number_param"]?.typeString)
        assertEquals(listOf("string_param"), schema.required)
    }

    @Test
    fun `test parse wrapped mcp json schema`() {
        val jsonString = """
            {
              "properties" : {
                "string_param" : {
                  "type" : {
                    "content" : "string",
                    "isString" : true
                  },
                  "description" : {
                    "content" : "A simple string parameter",
                    "isString" : true
                  }
                },
                "enum_param" : {
                  "type" : {
                    "content" : "string",
                    "isString" : true
                  },
                  "enum" : [ {
                    "content" : "RED",
                    "isString" : true
                  }, {
                    "content" : "GREEN",
                    "isString" : true
                  } ]
                }
              },
              "required" : [ "string_param" ],
              "type" : "object"
            }
        """.trimIndent()

        val schema = json.decodeFromString<ToolInputSchema>(jsonString)

        assertEquals("object", schema.typeString)
        assertEquals(2, schema.properties.size)
        
        val stringParam = schema.properties["string_param"]!!
        assertEquals("string", stringParam.typeString)
        assertEquals("A simple string parameter", stringParam.descriptionString)

        val enumParam = schema.properties["enum_param"]!!
        assertEquals("string", enumParam.typeString)
        assertEquals(listOf("RED", "GREEN"), enumParam.enumStrings)
    }
}
