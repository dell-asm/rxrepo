package com.slimgears.rxrepo.orientdb;

import com.orientechnologies.orient.core.db.OLiveQueryMonitor;
import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.exception.OConcurrentModificationException;
import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.executor.OResultSet;
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException;
import com.slimgears.rxrepo.query.Notification;
import com.slimgears.rxrepo.sql.SqlStatement;
import com.slimgears.rxrepo.sql.SqlStatementExecutor;
import com.slimgears.rxrepo.util.PropertyResolver;
import com.slimgears.util.generic.MoreStrings;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.slimgears.util.generic.LazyString.lazy;

class OrientDbStatementExecutor implements SqlStatementExecutor {
    private final static Logger log = LoggerFactory.getLogger(OrientDbStatementExecutor.class);
    private final OrientDbSessionProvider sessionProvider;
    private final Completable shutdown;

    OrientDbStatementExecutor(OrientDbSessionProvider sessionProvider, Completable shutdown) {
        this.shutdown = shutdown;
        this.sessionProvider = sessionProvider;
    }

    @Override
    public Observable<PropertyResolver> executeQuery(SqlStatement statement) {
        return toObservable(
                session -> {
                    logStatement("Querying", statement);
                    return session.query(statement.statement(), statement.args());
                });
    }

    @Override
    public Observable<PropertyResolver> executeCommandReturnEntries(SqlStatement statement) {
        return toObservable(
                session -> {
                    try {
                        logStatement("Executing command", statement);
                        return session.command(statement.statement(), statement.args());
                    } catch (OConcurrentModificationException | ORecordDuplicatedException e) {
                        throw new ConcurrentModificationException(e.getMessage(), e);
                    } catch (Throwable e) {
                        log.debug("Error when executing {}", lazy(() -> toString(statement)), e);
                        throw e;
                    }
                    finally {
                        logStatement("Executed command", statement);
                    }
                });
    }

    @Override
    public Single<Integer> executeCommandReturnCount(SqlStatement statement) {
        return executeCommandReturnEntries(statement)
                .map(res -> ((Long)res.getProperty("count", Long.class)).intValue())
                .first(0);
    }

    @Override
    public Completable executeCommand(SqlStatement statement) {
        return toObservable(session -> session.command(statement.statement(), statement.args()))
                .ignoreElements();
    }

    @Override
    public Observable<Notification<PropertyResolver>> executeLiveQuery(SqlStatement statement) {
        return Observable.<OrientDbLiveQueryListener.LiveQueryNotification>create(
                emitter -> {
                    logStatement("Live querying", statement);
                    sessionProvider.withSession(dbSession -> {
                        OLiveQueryMonitor monitor = dbSession.live(
                                statement.statement(),
                                new OrientDbLiveQueryListener(emitter),
                                statement.args());
                        emitter.setCancellable(monitor::unSubscribe);
                    });
                })
                .map(res -> Notification.ofModified(
                        Optional.ofNullable(res.oldResult())
                                .map(or -> OResultPropertyResolver.create(OrientDbSessionProvider.create(res.database()), or))
                                .orElse(null),
                        Optional.ofNullable(res.newResult())
                                .map(or -> OResultPropertyResolver.create(OrientDbSessionProvider.create(res.database()), or))
                                .orElse(null)))
                .takeUntil(shutdown.andThen(Observable.just(0)));
    }

    private Observable<PropertyResolver> toObservable(Function<ODatabaseDocument, OResultSet> resultSetSupplier) {
        return Observable.<OResult>create(
                emitter -> sessionProvider.withSession(dbSession -> {
                    OResultSet resultSet = resultSetSupplier.apply(dbSession);
                    resultSet.stream()
                            .peek(res -> log.trace("Received: {}", res))
                            .forEach(emitter::onNext);
                    resultSet.close();
                    emitter.onComplete();
                }))
                .map(res -> OResultPropertyResolver.create(sessionProvider, res));
    }

    private void logStatement(String title, SqlStatement statement) {
        log.trace("{}: {}", title, lazy(() -> toString(statement)));
    }

    private String toString(SqlStatement statement) {
        return statement.statement() + "(params: [" +
                IntStream.range(0, statement.args().length)
                        .mapToObj(i -> formatArg(i, statement.args()[i]))
                        .collect(Collectors.joining(", "));
    }

    private String formatArg(int index, Object obj) {
        String type = Optional.ofNullable(obj)
                .map(o -> o.getClass().getSimpleName())
                .orElse("null");
        return MoreStrings.format("#{}[{}]: {}", index, type, obj);
    }
}
