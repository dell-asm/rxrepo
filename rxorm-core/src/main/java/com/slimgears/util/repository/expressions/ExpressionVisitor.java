package com.slimgears.util.repository.expressions;

import com.slimgears.util.autovalue.annotations.PropertyMeta;
import com.slimgears.util.reflect.TypeToken;

public abstract class ExpressionVisitor<_T, _R> {
    public <S> _R visit(Expression<S> expression, _T arg) {
        if (expression instanceof PropertyExpression) {
            return visitProperty((PropertyExpression<S, ?, ?>)expression, arg);
        } else if (expression instanceof UnaryOperationExpression) {
            return visitUnaryOperator((UnaryOperationExpression<S, ?, ?>)expression, arg);
        } else if (expression instanceof BinaryOperationExpression) {
            return visitBinaryOperator((BinaryOperationExpression<S, ?, ?, ?>)expression, arg);
        } else if (expression instanceof ConstantExpression) {
            return visitConstant((ConstantExpression<S, ?>)expression, arg);
        } else if (expression instanceof ComposedExpression) {
            return visitComposition((ComposedExpression<S, ?, ?>)expression, arg);
        } else if (expression instanceof ArgumentExpression) {
            return visitArgument((ArgumentExpression<S, ?>)expression, arg);
        } else {
            return visitOther((ObjectExpression<S, ?>)expression, arg);
        }
    }

    protected abstract _R reduceBinary(Expression.Type type, _R first, _R second);
    protected abstract _R reduceUnary(Expression.Type type, _R first);

    protected abstract <S, T> _R visitOther(ObjectExpression<S, T> expression, _T arg);

    protected <S, T, R> _R visitComposition(ComposedExpression<S, T, R> expression, _T arg) {
        _R resSrc = this.visit(expression.source(), arg);
        _R resExp = this.visit(expression.expression(), arg);
        return reduceBinary(expression.type(), resSrc, resExp);
    }

    protected <S, T> _R visitConstant(ConstantExpression<S, T> constantExpression, _T arg) {
        return reduceUnary(constantExpression.type(), visitConstant(constantExpression.value(), arg));
    }

    protected <S, T, V> _R visitProperty(PropertyExpression<S, T, V> expression, _T arg) {
        return reduceBinary(expression.type(), visit(expression.target(), arg), visitProperty(expression.property(), arg));
    }

    protected <S, T1, T2, R> _R visitBinaryOperator(BinaryOperationExpression<S, T1, T2, R> expression, _T arg) {
        return reduceBinary(expression.type(), visit(expression.left(), arg), visit(expression.right(), arg));
    }

    protected <S, T, R> _R visitUnaryOperator(UnaryOperationExpression<S, T, R> expression, _T arg) {
        return reduceUnary(expression.type(), visit(expression.operand(), arg));
    }

    protected <S, T> _R visitArgument(ArgumentExpression<S, T> expression, _T arg) {
        return visitArgument(expression.argType(), arg);
    }

    protected abstract <T, V> _R visitProperty(PropertyMeta<T, V> propertyMeta, _T arg);
    protected abstract <V> _R visitConstant(V value, _T arg);
    protected abstract <T> _R visitArgument(TypeToken<T> argType, _T arg);
}
