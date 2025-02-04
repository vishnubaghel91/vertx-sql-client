/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.oracleclient.impl.commands;

import io.vertx.core.Future;
import io.vertx.core.impl.ContextInternal;
import io.vertx.oracleclient.OraclePrepareOptions;
import io.vertx.oracleclient.impl.Helper;
import io.vertx.oracleclient.impl.RowReader;
import io.vertx.sqlclient.PrepareOptions;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.impl.command.ExtendedQueryCommand;
import oracle.jdbc.OraclePreparedStatement;
import oracle.jdbc.OracleResultSet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Flow;

public class OracleCursorQueryCommand<C, R> extends QueryCommand<C, R> {
  private final ExtendedQueryCommand<R> command;
  private final Tuple params;

  public OracleCursorQueryCommand(ExtendedQueryCommand<R> command, Tuple params) {
    super(null);
    this.command = command;
    this.params = params;
  }

  @Override
  protected OraclePrepareOptions prepareOptions() {
    PrepareOptions prepareOptions = command.options();
    return prepareOptions instanceof OraclePrepareOptions ? (OraclePrepareOptions) prepareOptions : null;
  }

  @Override
  protected String query() {
    return command.sql();
  }

  @Override
  protected void applyStatementOptions(Statement statement) throws SQLException {
    String cursorId = command.cursorId();
    if (cursorId != null) {
      statement.setCursorName(cursorId);
    }

    int fetch = command.fetch();
    if (fetch > 0) {
      statement.setFetchSize(fetch);
    }
  }

  @Override
  protected void fillStatement(PreparedStatement ps, Connection conn) throws SQLException {
    for (int i = 0; i < params.size(); i++) {
      // we must convert types (to comply to JDBC)
      Object value = adaptType(conn, params.getValue(i));
      ps.setObject(i + 1, value);
    }
  }

  @Override
  protected Future<OracleResponse<R>> doExecute(OraclePreparedStatement ps, ContextInternal context, boolean returnAutoGeneratedKeys) {
    Flow.Publisher<OracleResultSet> publisher;
    try {
      publisher = ps.executeQueryAsyncOracle();
    } catch (SQLException e) {
      return context.failedFuture(e);
    }
    return Helper.first(publisher, context).compose(ors -> {
      try {
        return RowReader.create(ors, command.collector(), context, command.resultHandler());
      } catch (SQLException e) {
        return context.failedFuture(e);
      }
    }).compose(rr -> rr.read(command.fetch())).mapEmpty();
  }
}
