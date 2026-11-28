package com.comst19.dambom.buildlogic

data class ModuleDependencyEdge(
    val source: String,
    val configuration: String,
    val target: String,
) {
    fun encode(): String = "$source|$configuration|$target"

    companion object {
        fun decode(encoded: String): ModuleDependencyEdge {
            val parts = encoded.split('|')
            require(parts.size == 3) { "Invalid module dependency entry: $encoded" }
            return ModuleDependencyEdge(parts[0], parts[1], parts[2])
        }
    }
}

data class ModuleBoundaryViolation(
    val edge: ModuleDependencyEdge,
    val reason: String,
) {
    fun format(): String = "${edge.source} (${edge.configuration}) -> ${edge.target}: $reason"
}

private val PRODUCTION_SUFFIXES =
    listOf(
        "compileOnlyApi",
        "compileOnly",
        "implementation",
        "runtimeOnly",
        "api",
    )

private val TEST_PREFIX_TOKENS = setOf("test", "tests", "fixture", "fixtures")
private val CAMEL_CASE_TOKEN_REGEX = Regex("[A-Z]?[a-z0-9]+|[A-Z]+(?=[A-Z][a-z]|$)")
private val FEATURE_IMPL_REGEX = Regex("^:feature:[^:]+:impl$")
private val FEATURE_API_REGEX = Regex("^:feature:[^:]+:api$")
private val DATA_LAYER_MODULES = setOf(":core:data", ":core:database", ":core:datastore")
private val UPPER_LAYER_MODULES = setOf(":presentation", ":app")

fun isProductionDependencyConfiguration(name: String): Boolean {
    if (name.isEmpty()) return false
    val suffix =
        PRODUCTION_SUFFIXES.firstOrNull { base ->
            name == base ||
                (name.length > base.length && name.endsWith(base.replaceFirstChar(Char::uppercaseChar)))
        } ?: return false
    val prefix =
        if (name == suffix) {
            ""
        } else {
            name.removeSuffix(suffix.replaceFirstChar(Char::uppercaseChar))
        }
    if (prefix.isEmpty()) return true
    val tokens = CAMEL_CASE_TOKEN_REGEX.findAll(prefix).map { it.value.lowercase() }
    return tokens.none { it in TEST_PREFIX_TOKENS }
}

fun findModuleBoundaryViolations(edges: Iterable<ModuleDependencyEdge>): List<ModuleBoundaryViolation> =
    edges
        .asSequence()
        .filter { isProductionDependencyConfiguration(it.configuration) }
        .mapNotNull { edge -> violationReason(edge)?.let { reason -> ModuleBoundaryViolation(edge, reason) } }
        .sortedWith(compareBy({ it.edge.source }, { it.edge.configuration }, { it.edge.target }))
        .toList()

private fun violationReason(edge: ModuleDependencyEdge): String? {
    val source = edge.source
    val target = edge.target
    return when {
        source == ":core:domain" ->
            ":core:domain must remain pure and cannot declare direct project dependencies"

        FEATURE_API_REGEX.matches(source) ->
            when {
                FEATURE_IMPL_REGEX.matches(target) ->
                    "Feature API modules cannot depend on feature impl modules"

                target in DATA_LAYER_MODULES ->
                    "Feature API modules cannot depend on data layer modules"

                target == ":core:network" ->
                    "Feature API modules cannot depend on :core:network"

                target in UPPER_LAYER_MODULES ->
                    "Feature API modules cannot depend on upper layer modules"

                else -> null
            }

        FEATURE_IMPL_REGEX.matches(source) ->
            when {
                target in DATA_LAYER_MODULES ->
                    "Feature impl modules cannot depend on data layer modules"

                FEATURE_IMPL_REGEX.matches(target) && target != source ->
                    "Feature impl modules cannot depend on another feature impl module; depend on its :api module"

                target in UPPER_LAYER_MODULES ->
                    "Feature impl modules cannot depend on upper layer modules"

                else -> null
            }

        source == ":presentation" ->
            when {
                target in DATA_LAYER_MODULES ->
                    ":presentation cannot depend on data layer modules"

                target == ":core:network" ->
                    ":presentation cannot depend on :core:network"

                target == ":app" ->
                    ":presentation cannot depend on :app"

                else -> null
            }

        else -> null
    }
}
