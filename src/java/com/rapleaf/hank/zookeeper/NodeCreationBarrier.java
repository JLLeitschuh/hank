package com.rapleaf.hank.zookeeper;

import org.apache.log4j.Logger;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

class NodeCreationBarrier implements Watcher {

  private static final Logger LOG = Logger.getLogger(NodeCreationBarrier.class);

  private boolean waiting = true;
  private final String nodePath;
  private final ZooKeeper zk;

  // Will block until specified node is created or connection is lost
  public static void block(ZooKeeper zk, String nodePath) throws InterruptedException, KeeperException {
    new NodeCreationBarrier(zk, nodePath).block();
  }

  public NodeCreationBarrier(ZooKeeper zk, String nodePath) throws InterruptedException, KeeperException {
    this.nodePath = nodePath;
    this.zk = zk;
  }

  // Will block until specified node is created or connection is lost
  public synchronized void block() throws InterruptedException, KeeperException {
    // Wait only if it doesn't exist
    if (waiting && zk.exists(nodePath, this) == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Wait for creation of node " + nodePath);
      }
      this.wait();
    }
  }

  @Override
  public synchronized void process(WatchedEvent watchedEvent) {
    // If we lose synchronization, stop waiting
    if (watchedEvent.getState() != Event.KeeperState.SyncConnected) {
      waiting = false;
    } else {
      // If the node has been created, stop waiting
      if (watchedEvent.getType() == Event.EventType.NodeCreated) {
        waiting = false;
      }
    }
    // Notify threads to check waiting flag
    notifyAll();
  }
}
