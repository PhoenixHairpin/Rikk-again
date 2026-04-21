package me.rerere.ai.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

@Serializable
data class Tool(
    val name: String,
    val description: String,
    val parameters: () -> InputSchema? = { null },
    val systemPrompt: (model: Model, messages: List<UIMessage>) -> String = { _, _ -> "" },
    val needsApproval: Boolean = false,
    val execute: suspend (JsonElement) -> List<UIMessagePart>
)

@Serializable
sealed class InputSchema {
    @Serializable
    @SerialName("object")
    data class Obj(
        val properties: JsonObject,
        val required: List<String>? = null,
    ) : InputSchema()
}

data class ToolArgumentPreparation(
    val arguments: JsonElement,
    val corrections: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)

fun Tool.prepareArguments(rawInput: JsonElement): ToolArgumentPreparation {
    return parameters().prepareArguments(rawInput)
}

fun InputSchema?.prepareArguments(rawInput: JsonElement): ToolArgumentPreparation {
    return when (this) {
        null -> ToolArgumentPreparation(arguments = rawInput)
        is InputSchema.Obj -> prepareObjectArguments(rawInput)
    }
}

private fun InputSchema.Obj.prepareObjectArguments(rawInput: JsonElement): ToolArgumentPreparation {
    val corrections = mutableListOf<String>()
    val errors = mutableListOf<String>()
    val normalized = (rawInput as? JsonObject)?.toMutableMap() ?: mutableMapOf<String, JsonElement>().also {
        corrections += "Converted non-object tool input to an empty object because the schema requires named parameters."
    }

    properties.forEach { (name, schemaElement) ->
        val schema = schemaElement as? JsonObject ?: return@forEach
        val type = schema["type"]?.jsonPrimitive?.contentOrNull ?: return@forEach
        val value = normalized[name] ?: return@forEach
        val coerced = coerceValue(name = name, value = value, type = type)
        if (coerced.error != null) {
            errors += coerced.error
            return@forEach
        }
        if (coerced.value != null && coerced.value != value) {
            normalized[name] = coerced.value
        }
        coerced.correction?.let(corrections::add)
    }

    val missingRequired = required.orEmpty().filter { key ->
        val value = normalized[key]
        value == null || value is JsonPrimitive && value.contentOrNull == null
    }
    if (missingRequired.isNotEmpty()) {
        errors += "Missing required fields: ${missingRequired.joinToString()}"
    }

    return ToolArgumentPreparation(
        arguments = JsonObject(normalized),
        corrections = corrections,
        errors = errors,
    )
}

private data class ValueCoercion(
    val value: JsonElement? = null,
    val correction: String? = null,
    val error: String? = null,
)

private fun coerceValue(name: String, value: JsonElement, type: String): ValueCoercion {
    return when (type) {
        "string" -> when (value) {
            is JsonPrimitive -> {
                if (value.isString) {
                    ValueCoercion(value = value)
                } else {
                    ValueCoercion(
                        value = JsonPrimitive(value.content),
                        correction = "Converted field '$name' to string.",
                    )
                }
            }
            else -> ValueCoercion(error = "Field '$name' must be a string.")
        }

        "integer" -> {
            val number = (value as? JsonPrimitive)?.intOrNull
            if (number != null) {
                ValueCoercion(
                    value = JsonPrimitive(number),
                    correction = if ((value as JsonPrimitive).isString) "Converted field '$name' to integer." else null,
                )
            } else {
                ValueCoercion(error = "Field '$name' must be an integer.")
            }
        }

        "number" -> {
            val number = (value as? JsonPrimitive)?.doubleOrNull
            if (number != null) {
                ValueCoercion(
                    value = JsonPrimitive(number),
                    correction = if ((value as JsonPrimitive).isString) "Converted field '$name' to number." else null,
                )
            } else {
                ValueCoercion(error = "Field '$name' must be a number.")
            }
        }

        "boolean" -> {
            val primitive = value as? JsonPrimitive
            val bool = primitive?.booleanOrNull ?: primitive?.contentOrNull?.lowercase()?.let { raw ->
                when (raw) {
                    "true", "1" -> true
                    "false", "0" -> false
                    else -> null
                }
            }
            if (bool != null) {
                ValueCoercion(
                    value = JsonPrimitive(bool),
                    correction = if (primitive?.isString == true) "Converted field '$name' to boolean." else null,
                )
            } else {
                ValueCoercion(error = "Field '$name' must be a boolean.")
            }
        }

        "array" -> when (value) {
            is JsonArray -> ValueCoercion(value = value)
            else -> ValueCoercion(
                value = JsonArray(listOf(value)),
                correction = "Wrapped field '$name' into an array.",
            )
        }

        "object" -> when (value) {
            is JsonObject -> ValueCoercion(value = value)
            else -> ValueCoercion(error = "Field '$name' must be an object.")
        }

        else -> ValueCoercion(value = value)
    }
}
