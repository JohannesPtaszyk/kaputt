package dev.pott.kaputt.compiler

enum class MutationOperator(
    val key: String,
) {
    MATH("math"),
    CONDITIONAL_BOUNDARY("conditional-boundary"),
    NEGATE_CONDITIONAL("negate-conditional"),
    BOOLEAN_LITERAL("boolean-literal"),
    NUMBER_LITERAL("number-literal"),
    STRING_LITERAL("string-literal"),
    REMOVE_NOT("remove-not"),
    SAFE_CALL("safe-call"),
    VOID_CALL_REMOVAL("void-call-removal"),
    EMPTY_RETURN("empty-return"),
    RANGE_BOUNDARY("range-boundary"),
    LOGICAL_OPERATOR("logical-operator"),
    ;

    companion object {
        fun fromKeys(keys: List<String>): Set<MutationOperator> {
            if (keys.isEmpty()) return entries.toSet()
            val byKey = entries.associateBy(MutationOperator::key)
            return keys
                .map { key -> requireNotNull(byKey[key]) { "Unknown mutation operator '$key'" } }
                .toSet()
        }
    }
}
