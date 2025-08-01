package io.github.poelsk.authreloaded.database;

import com.zaxxer.hikari.HikariDataSource;

public interface IDataSource {
    HikariDataSource getDataSource();
}