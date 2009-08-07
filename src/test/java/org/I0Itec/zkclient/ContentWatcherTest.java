package org.I0Itec.zkclient;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.zookeeper.KeeperException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ContentWatcherTest {

    private static final String FILE_NAME = "/ContentWatcherTest";
    private ZkServer _zkServer;
    private ZkClient _zkClient;

    @Before
    public void setUp() throws Exception {
        _zkServer = TestUtil.startZkServer("ContentWatcherTest", 4711);
        _zkClient = _zkServer.getZkClient();
    }

    @After
    public void tearDown() throws Exception {
        if (_zkServer != null) {
            _zkServer.shutdown();
        }
    }

    @Test
    public void testGetContent() throws Exception {
        _zkClient.createPersistent(FILE_NAME, "a");
        final ContentWatcher<String> watcher = new ContentWatcher<String>(_zkClient, FILE_NAME);
        watcher.start();
        assertEquals("a", watcher.getContent());

        // update the content
        _zkClient.writeData(FILE_NAME, "b");

        String contentFromWatcher = TestUtil.waitUntil("b", new Callable<String>() {

            @Override
            public String call() throws Exception {
                return watcher.getContent();
            }
        }, TimeUnit.SECONDS, 5);

        assertEquals("b", contentFromWatcher);
        watcher.stop();
    }

    @Test
    public void testGetContentWaitTillCreated() throws InterruptedException, KeeperException, IOException {
        final Holder<String> contentHolder = new Holder<String>();

        Thread thread = new Thread() {
            @Override
            public void run() {
                ContentWatcher<String> watcher = new ContentWatcher<String>(_zkClient, FILE_NAME);
                try {
                    watcher.start();
                    contentHolder.set(watcher.getContent());
                    watcher.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        thread.start();

        // create content after 200ms
        Thread.sleep(200);
        _zkClient.createPersistent(FILE_NAME, "aaa");

        // we give the thread some time to pick up the change
        thread.join(1000);
        assertEquals("aaa", contentHolder.get());
    }

    @Test
    public void testHandlingNullContent() throws InterruptedException, KeeperException, IOException {
        _zkClient.createPersistent(FILE_NAME, null);
        ContentWatcher<String> watcher = new ContentWatcher<String>(_zkClient, FILE_NAME);
        watcher.start();
        assertEquals(null, watcher.getContent());
        watcher.stop();
    }

    @Test(timeout = 15000)
    public void testHandlingOfConnectionLoss() throws Exception {
        final Gateway gateway = new Gateway(4712, 4711);
        gateway.start();
        final ZkClient zkClient = new ZkClient("localhost:4712", 5000);

        // disconnect
        gateway.stop();

        // disconnect
        gateway.stop();

        // reconnect after 250ms and create file with content
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(250);
                    gateway.start();
                    zkClient.createPersistent(FILE_NAME, "aaa");
                } catch (Exception e) {
                    // ignore
                }
            }
        }.start();

        ContentWatcher<String> watcher = new ContentWatcher<String>(zkClient, FILE_NAME);
        watcher.start();
        assertEquals("aaa", watcher.getContent());
        watcher.stop();

        zkClient.close();
        gateway.stop();
    }
}
