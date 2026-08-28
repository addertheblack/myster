package com.general.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Base class for forward-compatible, string-identified enum-like wire values.
 *
 * <p>Subclasses expose canonical singleton constants and a {@code fromString} factory. The factory
 * can return a distinct non-canonical instance for an identifier introduced by a newer software
 * version, allowing serialized values to survive even when local behavior does not understand
 * them. Equality requires both the same concrete subclass and the same identifier.
 *
 * <p>Use a Java {@code enum} for ordinary closed value sets. This class is intended for values that
 * cross a version-skewed protocol boundary and must preserve identifiers unknown to the current
 * version. Raw string constants should not be used as an enum representation.
 *
 * <p>Identifier normalization and validation remain subtype responsibilities because different
 * serialized domains may have different case and character rules.
 *
 * @param <E> concrete enum-like subtype
 */
public abstract class TypeSafeEnum<E extends TypeSafeEnum<E>> {
    private final String identifier;
    private final boolean canonical;

    /** Creates a canonical value. */
    protected TypeSafeEnum(String identifier) {
        this(identifier, true);
    }

    /**
     * Creates a value with an explicit canonical state.
     *
     * @param identifier non-null serialized identifier
     * @param canonical whether the current software version recognizes the value
     */
    protected TypeSafeEnum(String identifier, boolean canonical) {
        this.identifier = Objects.requireNonNull(identifier, "Identifier cannot be null");
        this.canonical = canonical;
    }

    /** Returns the identifier used by the serialized representation. */
    public final String getIdentifier() {
        return identifier;
    }

    /** Returns whether this value is recognized by the current software version. */
    public final boolean isCanonical() {
        return canonical;
    }

    /**
     * Builds an immutable identifier index for a subtype's canonical singleton values.
     *
     * @param values canonical values to index
     * @param <T> concrete enum-like subtype
     * @return immutable identifier-to-value map
     * @throws IllegalArgumentException if a value is non-canonical or an identifier is duplicated
     */
    @SafeVarargs
    protected static <T extends TypeSafeEnum<T>> Map<String, T> canonicalValueMap(T... values) {
        Map<String, T> indexedValues = new LinkedHashMap<>();
        for (T value : values) {
            Objects.requireNonNull(value, "Canonical value cannot be null");
            if (!value.isCanonical()) {
                throw new IllegalArgumentException(
                        "Cannot register non-canonical value: " + value.getIdentifier());
            }
            T previous = indexedValues.put(value.getIdentifier(), value);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate canonical identifier: " + value.getIdentifier());
            }
        }
        return Map.copyOf(indexedValues);
    }

    /**
     * Resolves an identifier to a canonical singleton or creates a preserved unknown value.
     *
     * @param identifier normalized, validated identifier
     * @param canonicalValues subtype-specific canonical value index
     * @param unknownFactory factory for a non-canonical value
     * @param <T> concrete enum-like subtype
     * @return canonical singleton when registered, otherwise the factory result
     * @throws IllegalArgumentException if the factory returns a canonical value or changes the
     *         identifier
     */
    protected static <T extends TypeSafeEnum<T>> T from(
            String identifier,
            Map<String, T> canonicalValues,
            Function<String, T> unknownFactory) {
        Objects.requireNonNull(identifier, "Identifier cannot be null");
        Objects.requireNonNull(unknownFactory, "Unknown factory cannot be null");
        T known = Objects.requireNonNull(
                canonicalValues, "Canonical value map cannot be null").get(identifier);
        if (known != null) {
            return known;
        }

        T unknown = Objects.requireNonNull(unknownFactory.apply(identifier));
        if (unknown.isCanonical()) {
            throw new IllegalArgumentException("Unknown factory returned a canonical value");
        }
        if (!identifier.equals(unknown.getIdentifier())) {
            throw new IllegalArgumentException("Unknown factory changed the identifier");
        }
        return unknown;
    }

    @Override
    public final boolean equals(Object object) {
        return this == object
                || object != null
                && getClass() == object.getClass()
                && identifier.equals(((TypeSafeEnum<?>) object).identifier);
    }

    @Override
    public final int hashCode() {
        return 31 * getClass().hashCode() + identifier.hashCode();
    }

    @Override
    public String toString() {
        return identifier + (canonical ? "" : " (non-canonical)");
    }
}
