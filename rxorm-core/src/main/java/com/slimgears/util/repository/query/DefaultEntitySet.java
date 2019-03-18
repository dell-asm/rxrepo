package com.slimgears.util.repository.query;

import com.google.common.collect.ImmutableList;
import com.slimgears.util.autovalue.annotations.HasMetaClass;
import com.slimgears.util.autovalue.annotations.HasMetaClassWithKey;
import com.slimgears.util.autovalue.annotations.MetaClassWithKey;
import com.slimgears.util.repository.expressions.Aggregator;
import com.slimgears.util.repository.expressions.BooleanExpression;
import com.slimgears.util.repository.expressions.ObjectExpression;
import com.slimgears.util.repository.expressions.PropertyExpression;
import com.slimgears.util.repository.expressions.UnaryOperationExpression;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultEntitySet<K, S extends HasMetaClassWithKey<K, S>> implements EntitySet<K, S> {
    private final QueryProvider queryProvider;
    private final MetaClassWithKey<K, S> metaClass;

    private DefaultEntitySet(QueryProvider queryProvider, MetaClassWithKey<K, S> metaClass) {
        this.queryProvider = queryProvider;
        this.metaClass = metaClass;
    }

    static <K, S extends HasMetaClassWithKey<K, S>> DefaultEntitySet<K, S> create(QueryProvider queryProvider, MetaClassWithKey<K, S> metaClass) {
        return new DefaultEntitySet<>(queryProvider, metaClass);
    }

    @Override
    public MetaClassWithKey<K, S> metaClass() {
        return metaClass;
    }

    @Override
    public EntityDeleteQuery<K, S> delete() {
        return new EntityDeleteQuery<K, S>() {
            private final AtomicReference<BooleanExpression<S>> predicate = new AtomicReference<>();
            private final DeleteInfo.Builder<K, S> builder = DeleteInfo.builder();

            @Override
            public Completable execute() {
                return queryProvider.delete(builder
                        .metaClass(metaClass)
                        .predicate(predicate.get())
                        .build());
            }

            @Override
            public EntityDeleteQuery<K, S> where(BooleanExpression<S> predicate) {
                this.predicate.updateAndGet(exp -> Optional.ofNullable(exp).map(ex -> ex.and(predicate)).orElse(predicate));
                return this;
            }

            @Override
            public EntityDeleteQuery<K, S> limit(long limit) {
                builder.limit(limit);
                return this;
            }

            @Override
            public EntityDeleteQuery<K, S> skip(long skip) {
                builder.skip(skip);
                return this;
            }
        };
    }

    @Override
    public EntityUpdateQuery<K, S> update() {
        return new EntityUpdateQuery<K, S>() {
            private final AtomicReference<BooleanExpression<S>> predicate = new AtomicReference<>();
            private final UpdateInfo.Builder<K, S> builder = UpdateInfo.builder();

            @Override
            public <T extends HasMetaClass<T>, V> EntityUpdateQuery<K, S> set(PropertyExpression<S, T, V> property, ObjectExpression<S, V> value) {
                builder.propertyUpdatesBuilder().add(PropertyUpdateInfo.create(property, value));
                return this;
            }

            @Override
            public Observable<S> execute() {
                return queryProvider.update(builder
                        .predicate(predicate.get())
                        .build());
            }

            @Override
            public EntityUpdateQuery<K, S> where(BooleanExpression<S> predicate) {
                this.predicate.updateAndGet(exp -> Optional.ofNullable(exp).map(ex -> ex.and(predicate)).orElse(predicate));
                return this;
            }

            @Override
            public EntityUpdateQuery<K, S> limit(long limit) {
                builder.limit(limit);
                return this;
            }

            @Override
            public EntityUpdateQuery<K, S> skip(long skip) {
                builder.skip(skip);
                return this;
            }
        };
    }

    @Override
    public SelectQueryBuilder<K, S> query() {
        return new SelectQueryBuilder<K, S>() {
            private final ImmutableList.Builder<SortingInfo<S, S, ?>> sortingInfos = ImmutableList.builder();
            private final AtomicReference<BooleanExpression<S>> predicate = new AtomicReference<>();
            private Long limit;
            private Long skip;

            @Override
            public <V> SelectQueryBuilder<K, S> orderBy(PropertyExpression<S, S, V> field, boolean ascending) {
                sortingInfos.add(SortingInfo.create(ascending, field));
                return this;
            }

            @Override
            public SelectQuery<S> select() {
                return select(ObjectExpression.arg(metaClass.objectClass()));
            }

            @Override
            public <T> SelectQuery<T> select(ObjectExpression<S, T> expression) {
                return new SelectQuery<T>() {
                    private final QueryInfo.Builder<K, S, T> builder = QueryInfo.<K, S, T>builder()
                            .metaClass(metaClass)
                            .predicate(predicate.get())
                            .limit(limit)
                            .skip(skip)
                            .sorting(sortingInfos.build())
                            .mapping(expression);

                    @Override
                    public Maybe<T> first() {
                        QueryInfo<K, S, T> query = builder.limit(1L).build();
                        return queryProvider
                                .query(query)
                                .singleElement();
                    }

                    @Override
                    public <R, E extends UnaryOperationExpression<T, Collection<T>, R>> Single<R> aggregate(Aggregator<T, T, R, E> aggregator) {
                        QueryInfo<K, S, T> query = builder.build();
                        return queryProvider.aggregate(query, aggregator);
                    }

                    @Override
                    public Observable<T> retrieve(PropertyExpression<T, ?, ?>... properties) {
                        QueryInfo<K, S, T> query = builder
                                .propertiesAdd(properties)
                                .build();

                        return queryProvider.query(query);
                    }
                };
            }

            @Override
            public LiveSelectQuery<S> liveSelect() {
                return liveSelect(ObjectExpression.arg(metaClass.objectClass()));
            }

            @Override
            public <T> LiveSelectQuery<T> liveSelect(ObjectExpression<S, T> expression) {
                return new LiveSelectQuery<T>() {
                    private final QueryInfo.Builder<K, S, T> builder = QueryInfo.<K, S, T>builder()
                            .metaClass(metaClass)
                            .predicate(predicate.get())
                            .mapping(expression);

                    @Override
                    public Observable<T> first() {
                        QueryInfo<K, S, T> query = builder.limit(1L).build();
                        return queryProvider
                                .liveQuery(query)
                                .flatMapMaybe(n -> queryProvider.query(query).singleElement());
                    }

                    @Override
                    public Observable<List<? extends T>> toList() {
                        QueryInfo<K, S, T> query = builder.build();
                        return queryProvider
                                .liveQuery(query)
                                .switchMapSingle(n -> queryProvider
                                        .query(query)
                                        .toList());
                    }

                    @Override
                    public <R, E extends UnaryOperationExpression<T, Collection<T>, R>> Observable<R> aggregate(Aggregator<T, T, R, E> aggregator) {
                        QueryInfo<K, S, T> query = builder.build();
                        return queryProvider.aggregate(query, aggregator)
                                .toObservable()
                                .concatWith(queryProvider.liveAggregate(query, aggregator));
                    }

                    @Override
                    public Observable<Notification<T>> observe(PropertyExpression<T, ?, ?>... properties) {
                        QueryInfo<K, S, T> query = builder
                                .propertiesAdd(properties)
                                .build();
                        return queryProvider.query(query)
                                .map(Notification::ofCreated)
                                .concatWith(queryProvider.liveQuery(query));
                    }
                };
            }

            @Override
            public SelectQueryBuilder<K, S> where(BooleanExpression<S> predicate) {
                this.predicate.updateAndGet(exp -> Optional.ofNullable(exp).map(ex -> ex.and(predicate)).orElse(predicate));
                return this;
            }

            @Override
            public SelectQueryBuilder<K, S> limit(long limit) {
                this.limit = limit;
                return this;
            }

            @Override
            public SelectQueryBuilder<K, S> skip(long skip) {
                this.skip = skip;
                return this;
            }
        };
    }

    @Override
    public Single<List<S>> update(Iterable<S> entities) {
        return queryProvider.update(metaClass, entities);
    }
}
