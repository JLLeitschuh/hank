/**
 *  Copyright 2011 Rapleaf
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.rapleaf.hank.ring_group_conductor;

import com.rapleaf.hank.coordinator.*;
import com.rapleaf.hank.coordinator.mock.MockDomainGroup;
import junit.framework.TestCase;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;

public class TestRingGroupUpdateTransitionFunctionImpl extends TestCase {

  private static Logger LOG = Logger.getLogger(TestRingGroupUpdateTransitionFunctionImpl.class);

  private class MockRingLocal extends MockRing {

    protected DomainGroupVersion assignedVersion = null;

    public MockRingLocal(int number,
                         Set<Host> hosts) {
      super(hosts, null, number);
    }

    public void setAssignedVersion(DomainGroupVersion domainGroupVersion) {
      assignedVersion = domainGroupVersion;
    }

    public boolean isUpToDate(DomainGroupVersion domainGroupVersion) {
      for (Host host : getHosts()) {
        if (!((MockHostLocal) host).isUpToDate(domainGroupVersion)) {
          return false;
        }
      }
      return true;
    }

    public boolean isAssigned(DomainGroupVersion domainGroupVersion) {
      return assignedVersion != null && assignedVersion.equals(domainGroupVersion);
    }
  }

  private class MockRingGroupLocal extends MockRingGroup {

    public MockRingGroupLocal(Ring... rings) {
      super(domainGroup, "myRingGroup", new LinkedHashSet<Ring>(Arrays.asList(rings)), null);
    }
  }

  private class MockHostLocal extends MockHost {

    private DomainGroupVersion currentVersion = null;

    public MockHostLocal(PartitionServerAddress address) {
      super(address);
    }

    public boolean isUpToDate(DomainGroupVersion domainGroupVersion) {
      return currentVersion != null && currentVersion.equals(domainGroupVersion);
    }

    public void setCurrentVersion(DomainGroupVersion domainGroupVersion) {
      this.currentVersion = domainGroupVersion;
    }
  }

  private static DomainGroup domainGroup = new MockDomainGroup("myDomainGroup") {

    @Override
    public DomainGroupVersion getVersionByNumber(int versionNumber) {
      switch (versionNumber) {
        case 1:
          return v1;
        case 2:
          return v2;
        case 3:
          return v3;
        default:
          throw new RuntimeException("Unknown version: " + versionNumber);
      }
    }
  };

  private static DomainGroupVersion v1 =
      new MockDomainGroupVersion(Collections.<DomainGroupVersionDomainVersion>emptySet(), domainGroup, 1);
  private static DomainGroupVersion v2 =
      new MockDomainGroupVersion(Collections.<DomainGroupVersionDomainVersion>emptySet(), domainGroup, 2);
  private static DomainGroupVersion v3 =
      new MockDomainGroupVersion(Collections.<DomainGroupVersionDomainVersion>emptySet(), domainGroup, 3);

  private MockRingLocal r0;
  private MockRingLocal r1;
  private MockRingLocal r2;

  private MockHostLocal r0h0 = null;
  private MockHostLocal r0h1 = null;
  private MockHostLocal r1h0 = null;
  private MockHostLocal r1h1 = null;
  private MockHostLocal r2h0 = null;
  private MockHostLocal r2h1 = null;

  private MockRingGroupLocal rg = null;

  private RingGroupUpdateTransitionFunctionImpl transitionFunction = null;

  @Override
  public void setUp() throws IOException {

    LOG.info("----- Test start -----");

    r0h0 = new MockHostLocal(new PartitionServerAddress("localhost", 1));
    r0h1 = new MockHostLocal(new PartitionServerAddress("localhost", 2));
    r1h0 = new MockHostLocal(new PartitionServerAddress("localhost", 3));
    r1h1 = new MockHostLocal(new PartitionServerAddress("localhost", 4));
    r2h0 = new MockHostLocal(new PartitionServerAddress("localhost", 5));
    r2h1 = new MockHostLocal(new PartitionServerAddress("localhost", 6));

    Set<Host> r0Hosts = new HashSet<Host>();
    r0Hosts.add(r0h0);
    r0Hosts.add(r0h1);
    Set<Host> r1Hosts = new HashSet<Host>();
    r1Hosts.add(r1h0);
    r1Hosts.add(r1h1);
    Set<Host> r2Hosts = new HashSet<Host>();
    r2Hosts.add(r2h0);
    r2Hosts.add(r2h1);

    r0 = new MockRingLocal(0, r0Hosts);
    r1 = new MockRingLocal(1, r1Hosts);
    r2 = new MockRingLocal(2, r2Hosts);

    rg = new MockRingGroupLocal(r0, r1, r2);

    transitionFunction = new RingGroupUpdateTransitionFunctionImpl() {
      @Override
      protected boolean isAssigned(Ring ring, DomainGroupVersion domainGroupVersion) {
        return ((MockRingLocal) ring).isAssigned(domainGroupVersion);
      }

      @Override
      protected boolean isUpToDate(Ring ring, DomainGroupVersion domainGroupVersion) {
        return ((MockRingLocal) ring).isUpToDate(domainGroupVersion);
      }

      @Override
      protected boolean isUpToDate(Host host, DomainGroupVersion domainGroupVersion) {
        return ((MockHostLocal) host).isUpToDate(domainGroupVersion);
      }

      @Override
      protected void assign(Ring ring, DomainGroupVersion domainGroupVersion) {
        ((MockRingLocal) ring).setAssignedVersion(domainGroupVersion);
      }
    };
  }

  private void setUpRing(MockRingLocal ring, DomainGroupVersion assignedVersion, HostState hostState) throws IOException {
    ring.setAssignedVersion(assignedVersion);
    for (Host host : ring.getHosts()) {
      host.setState(hostState);
      ((MockHostLocal) host).setCurrentVersion(assignedVersion);
    }
  }

  public void testNothingToDo() throws IOException {
    rg.setTargetVersion(1);
    setUpRing(r0, v1, HostState.SERVING);
    setUpRing(r1, v1, HostState.SERVING);
    setUpRing(r2, v1, HostState.SERVING);

    transitionFunction.manageTransitions(rg);

    // No commands should have been issued
    assertNull(r0h0.getLastEnqueuedCommand());
    assertNull(r0h1.getLastEnqueuedCommand());
    assertNull(r1h0.getLastEnqueuedCommand());
    assertNull(r1h1.getLastEnqueuedCommand());
    assertNull(r2h0.getLastEnqueuedCommand());
    assertNull(r2h1.getLastEnqueuedCommand());
  }

  public void testTakesDownFirstRingWhenStartingUpdate() throws IOException {
    rg.setTargetVersion(2);

    setUpRing(r0, v1, HostState.SERVING);
    setUpRing(r1, v1, HostState.SERVING);
    setUpRing(r2, v1, HostState.SERVING);

    transitionFunction.manageTransitions(rg);

    // All serving hosts in r1 should receive go to idle
    assertEquals(HostCommand.GO_TO_IDLE, r0h0.getLastEnqueuedCommand());
    assertEquals(HostCommand.GO_TO_IDLE, r0h1.getLastEnqueuedCommand());

    // No commands should have been issued to other rings
    assertNull(r1h0.getLastEnqueuedCommand());
    assertNull(r1h1.getLastEnqueuedCommand());
    assertNull(r2h0.getLastEnqueuedCommand());
    assertNull(r2h1.getLastEnqueuedCommand());
  }

  public void testAssignIdleRing() throws IOException {
    rg.setTargetVersion(2);

    setUpRing(r0, v1, HostState.IDLE);
    setUpRing(r1, v1, HostState.SERVING);
    setUpRing(r2, v1, HostState.SERVING);

    transitionFunction.manageTransitions(rg);

    // v2 should have been assigned to r0
    assertTrue(r0.isAssigned(v2));

    // No commands should have been issued to rings
    assertNull(r0h0.getLastEnqueuedCommand());
    assertNull(r0h1.getLastEnqueuedCommand());
    assertNull(r1h0.getLastEnqueuedCommand());
    assertNull(r1h1.getLastEnqueuedCommand());
    assertNull(r2h0.getLastEnqueuedCommand());
    assertNull(r2h1.getLastEnqueuedCommand());
  }

  //TODO: write more tests
}
