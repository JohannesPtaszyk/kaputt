package dev.pott.kaputt.runtime

/** Excludes a declaration and everything nested inside it from mutation testing. */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
)
@Retention(AnnotationRetention.BINARY)
annotation class DisableMutation
