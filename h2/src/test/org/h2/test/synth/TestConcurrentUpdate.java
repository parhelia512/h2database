/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.synth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import org.h2.test.TestBase;
import org.h2.test.TestDb;
import org.h2.util.Task;

/**
 * A concurrent test.
 */
public class TestConcurrentUpdate extends TestDb {

    private static final int THREADS = 10;
    private static final int ROW_COUNT = 3;

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        org.h2.test.TestAll config = new org.h2.test.TestAll();
//        config.memory = true;
//        config.mvStore = false;
        System.out.println(config);
        TestBase test = createCaller().init(config);
        for (int i = 0; i < 10; i++) {
            System.out.println("Pass #" + i);
            test.testFromMain();
        }
    }

    @Override
    public void test() throws Exception {
        testConcurrent();
        testConcurrentShutdown();
    }

    private void testConcurrent() throws Exception {
        deleteDb("concurrent");
        final String url = getURL("concurrent;LOCK_TIMEOUT=2000", true);
        try (Connection conn = getConnection(url)) {
            Statement stat = conn.createStatement();
            stat.execute("create table test(id int primary key, name varchar)");

            Task[] tasks = new Task[THREADS];
            for (int i = 0; i < THREADS; i++) {
                final int threadId = i;
                Task t = new Task() {
                    @Override
                    public void call() throws Exception {
                        Random r = new Random(threadId);
                        try (Connection conn = getConnection(url)) {
                            PreparedStatement insert = conn.prepareStatement(
                                    "merge into test values(?, ?)");
                            PreparedStatement update = conn.prepareStatement(
                                    "update test set name = ? where id = ?");
                            PreparedStatement delete = conn.prepareStatement(
                                    "delete from test where id = ?");
                            PreparedStatement select = conn.prepareStatement(
                                    "select * from test where id = ?");
                            while (!stop) {
                                int x = r.nextInt(ROW_COUNT);
                                String data = "x" + r.nextInt(ROW_COUNT);
                                switch (r.nextInt(4)) {
                                    case 0:
                                        insert.setInt(1, x);
                                        insert.setString(2, data);
                                        insert.execute();
                                        break;
                                    case 1:
                                        update.setString(1, data);
                                        update.setInt(2, x);
                                        update.execute();
                                        break;
                                    case 2:
                                        delete.setInt(1, x);
                                        delete.execute();
                                        break;
                                    case 3:
                                        select.setInt(1, x);
                                        ResultSet rs = select.executeQuery();
                                        while (rs.next()) {
                                            rs.getString(2);
                                        }
                                        break;
                                }
                            }
                        }
                    }
                };
                tasks[i] = t;
                t.execute();
            }
            // test 2 seconds
            Thread.sleep(2000);
            boolean success = true;
            for (Task t : tasks) {
                t.join();
                Throwable exception = t.getException();
                if (exception != null) {
                    logError("", exception);
                    success = false;
                }
            }
            assert success;
        }
    }

    private void testConcurrentShutdown() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb(getTestName());
        final String url = getURL(getTestName(), true);
        try (Connection connection = getConnection(url)) {
            connection.createStatement().execute("create table test(id int primary key, v int)");
            connection.createStatement().execute("insert into test values(0, 0)");
        }
        int len = 2;
        final CountDownLatch latch = new CountDownLatch(len + 1);
        Collection<Task> tasks = new ArrayList<>();

        tasks.add(new Task() {
            @Override
            public void call() throws Exception {
                try (Connection c = getConnection(url)) {
                    c.setAutoCommit(false);
                    c.createStatement().execute("insert into test values(1, 1)");
                    latch.countDown();
                    latch.await();
                }
            }
        });

        for (int i = 0; i < len; i++) {
            tasks.add(new Task() {
                @Override
                public void call() throws Exception {
                    try (Connection c = getConnection(url)) {
                        Statement stmt = c.createStatement();
                        latch.countDown();
                        latch.await();
                        stmt.execute("shutdown");
                    }
                }
            });
        }
        for (Task task : tasks) {
            task.execute();
        }
        for (Task task : tasks) {
            task.getException();
        }
        try (Connection connection = getConnection(getTestName())) {
            ResultSet rs = connection.createStatement().executeQuery("select count(*) from test");
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }
}
