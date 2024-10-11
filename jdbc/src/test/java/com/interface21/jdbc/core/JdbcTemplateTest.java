package com.interface21.jdbc.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JdbcTemplateTest {

    private final DataSource dataSource = mock();
    private final Connection connection = mock();
    private final PreparedStatement preparedStatement = mock();
    private final ResultSet resultSet = mock();

    private JdbcTemplate sut;

    @BeforeEach
    void setup() throws SQLException {
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.prepareStatement(anyString())).willReturn(preparedStatement);
        given(preparedStatement.getConnection()).willReturn(connection);
        given(preparedStatement.executeQuery()).willReturn(resultSet);

        sut = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("Query an object from the datasource.")
    void queryForObject() throws SQLException {
        // given
        final var sql = "select * from users where id = ?";
        final var saved = new User(1L, "gugu");

        given(resultSet.next()).willReturn(true);
        given(resultSet.getLong("id")).willReturn(saved.id);
        given(resultSet.getString("account")).willReturn(saved.account);

        // when
        final var actual = sut.queryForObject(sql, userRowMapper, 1L);

        // then
        assertThat(actual.id).isEqualTo(saved.id);
        assertThat(actual.account).isEqualTo(saved.account);
        verify(connection).close();
        verify(preparedStatement).close();
        verify(resultSet).close();
    }

    @Test
    @DisplayName("Query an object from the datasource.")
    void queryForList() throws SQLException {
        // given
        final var sql = "select * from users";
        final var saved1 = new User(1L, "gugu");
        final var saved2 = new User(2L, "wonny");
        final var saved3 = new User(3L, "lisa");

        given(resultSet.next()).willReturn(true, true, true, false);
        given(resultSet.getLong("id")).willReturn(saved1.id, saved2.id, saved3.id);
        given(resultSet.getString("account")).willReturn(saved1.account, saved2.account, saved3.account);

        // when
        final var actual = sut.queryForList(sql, userRowMapper);

        // then
        assertThat(actual)
                .hasSize(3)
                .containsExactly(saved1, saved2, saved3);
        verify(connection).close();
        verify(preparedStatement).close();
        verify(resultSet).close();
    }

    @Test
    @DisplayName("Update a row from the datasource.")
    void update() throws SQLException {
        // given
        final var sql = "update users account = ? where id = ?";
        final var updated = new User(1L, "left hand");

        // when
        sut.update(sql, updated.account, updated.id);

        // then
        verify(preparedStatement).setObject(1, updated.account);
        verify(preparedStatement).setObject(2, updated.id);
        verify(connection).close();
        verify(preparedStatement).close();
    }

    @Test
    @DisplayName("Query an object from the datasource.")
    void executeQueryForObject() throws SQLException {
        // given
        final var saved = new User(1L, "gugu");

        given(resultSet.next()).willReturn(true);
        given(resultSet.getLong("id")).willReturn(saved.id);
        given(resultSet.getString("account")).willReturn(saved.account);

        class UserPreparedStatementSetter implements PreparedStatementSetter {

            final long id;

            UserPreparedStatementSetter(long id) {
                this.id = id;
            }

            @Override
            public void setValues(PreparedStatement preparedStatement) throws SQLException {
                preparedStatement.setLong(1, id);
            }
        }

        class UserResultSetExtractor implements ResultSetExtractor<User> {
            @Override
            public User extract(ResultSet resultSet) throws SQLException {
                if (resultSet.next()) {
                    return userRowMapper.mapRow(resultSet, resultSet.getRow());
                }
                return null;
            }
        }

        // when
        final var actual = sut.execute(
                "select * from users where id = ?",
                new UserPreparedStatementSetter(saved.id),
                new UserResultSetExtractor());

        // then
        assertThat(actual).isEqualTo(saved);
    }

    @Test
    @DisplayName("Query objects from the datasource.")
    void executeQueryForList() throws SQLException {
        // given
        final var saved1 = new User(1L, "gugu");
        final var saved2 = new User(2L, "wonny");
        final var saved3 = new User(3L, "tommy");

        given(resultSet.next()).willReturn(true, true, false);
        given(resultSet.getLong("id")).willReturn(saved1.id, saved3.id);
        given(resultSet.getString("account")).willReturn(saved1.account, saved3.account);

        class PairUserPreparedStatementSetter implements PreparedStatementSetter {

            final long id1;
            final long id2;

            PairUserPreparedStatementSetter(long id11, long id21) {
                this.id1 = id11;
                this.id2 = id21;
            }

            @Override
            public void setValues(PreparedStatement preparedStatement) throws SQLException {
                preparedStatement.setLong(1, id1);
                preparedStatement.setLong(2, id2);
            }
        }

        class PairUserResultSetExtractor implements ResultSetExtractor<List<User>> {

            static final int SIZE = 2;

            @Override
            public List<User> extract(ResultSet resultSet) throws SQLException {
                final var users = new ArrayList<User>();
                while (resultSet.next()) {
                    final var user = userRowMapper.mapRow(resultSet, resultSet.getRow());
                    users.add(user);
                }
                if (users.size() != SIZE) {
                    throw new IllegalArgumentException("Pair must be 2!");
                }
                return users;
            }
        }

        // when
        final var actual = sut.execute(
                "select * from users where id in (?, ?)",
                new PairUserPreparedStatementSetter(saved1.id, saved3.id),
                new PairUserResultSetExtractor());

        // then
        assertThat(actual)
                .containsExactly(saved1, saved3)
                .doesNotContain(saved2);
    }

    private record User(Long id, String account) {}

    private final RowMapper<User> userRowMapper = (resultSet, __) -> new User(
            resultSet.getLong("id"),
            resultSet.getString("account"));
}
