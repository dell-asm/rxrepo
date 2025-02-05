package com.slimgears.rxrepo.expressions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.reflect.TypeToken;
import com.slimgears.rxrepo.expressions.internal.*;
import com.slimgears.util.autovalue.annotations.PropertyMeta;

import java.util.Collection;

public interface PropertyExpression<S, T, V> extends ObjectExpression<S, V> {
    @Override
    default TypeToken<V> objectType() {
        return property().type();
    }

    @JsonProperty ObjectExpression<S, T> target();
    @JsonProperty PropertyMeta<T, V> property();

    String path();

    default StringExpression<S> asString() {
        return StringUnaryOperationExpression.create(Type.AsString, this);
    }

    static <S, T, V> ObjectPropertyExpression<S, T, V> ofObject(ObjectExpression<S, T> target, PropertyMeta<T, V> property) {
        return ObjectPropertyExpression.create(Type.Property, target, property);
    }

    static <S, V> ObjectPropertyExpression<S, S, V> ofObject(PropertyMeta<S, V> property) {
        return ofObject(ObjectExpression.arg(property.declaringType().asType()), property);
    }

    static <S, T, V extends Comparable<V>> ComparablePropertyExpression<S, T, V> ofComparable(ObjectExpression<S, T> target, PropertyMeta<T, V> property) {
        return ComparablePropertyExpression.create(Type.ComparableProperty, target, property);
    }

    static <S, V extends Comparable<V>> ComparablePropertyExpression<S, S, V> ofComparable(PropertyMeta<S, V> property) {
        return ofComparable(ObjectExpression.arg(property.declaringType().asType()), property);
    }

    static <S, T> StringPropertyExpression<S, T> ofString(ObjectExpression<S, T> target, PropertyMeta<T, String> property) {
        return StringPropertyExpression.create(Type.StringProperty, target, property);
    }

    static <S> StringPropertyExpression<S, S> ofString(PropertyMeta<S, String> property) {
        return ofString(ObjectExpression.arg(property.declaringType().asType()), property);
    }

    static <S, T> BooleanPropertyExpression<S, T> ofBoolean(ObjectExpression<S, T> target, PropertyMeta<T, Boolean> property) {
        return BooleanPropertyExpression.create(Type.BooleanProperty, target, property);
    }

    static <S> BooleanPropertyExpression<S, S> ofBoolean(PropertyMeta<S, Boolean> property) {
        return ofBoolean(ObjectExpression.arg(property.declaringType().asType()), property);
    }

    static <S, T, V extends Number & Comparable<V>> NumericPropertyExpression<S, T, V> ofNumeric(ObjectExpression<S, T> target, PropertyMeta<T, V> property) {
        return NumericPropertyExpression.create(Type.NumericProperty, target, property);
    }

    static <S, V extends Number & Comparable<V>> NumericPropertyExpression<S, S, V> ofNumeric(PropertyMeta<S, V> property) {
        return ofNumeric(ObjectExpression.arg(property.declaringType().asType()), property);
    }

    static <S, T, E, C extends Collection<E>> CollectionPropertyExpression<S, T, E, C> ofCollection(ObjectExpression<S, T> target, PropertyMeta<T, C> property) {
        return CollectionPropertyExpression.create(Type.CollectionProperty, target, property);
    }

    static <S, E, C extends Collection<E>> CollectionPropertyExpression<S, S, E, C> ofCollection(PropertyMeta<S, C> property) {
        return ofCollection(ObjectExpression.arg(property.declaringType().asType()), property);
    }
}
