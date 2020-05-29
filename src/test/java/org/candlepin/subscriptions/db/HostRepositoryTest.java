/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.subscriptions.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
@TestPropertySource("classpath:/test.properties")
class HostRepositoryTest {

    @Autowired
    private HostRepository repo;

    @Transactional
    @BeforeAll
    void setupTestData() {

        // ACCOUNT 1 HOSTS
        Host host1 = createHost("insights1", "account1", "org1");
        addBucketToHost(host1, "RHEL", ServiceLevel.PREMIUM, false);
        addBucketToHost(host1, "Satellite", ServiceLevel.PREMIUM, false);

        Host host2 = createHost("insights2", "account1", "org1");
        addBucketToHost(host2, "RHEL", ServiceLevel.SELF_SUPPORT, false);
        addBucketToHost(host2, "Satellite", ServiceLevel.SELF_SUPPORT, false);

        // ACCOUNT 2 HOSTS
        Host host3 = createHost("insights3", "account2", "org2");
        addBucketToHost(host3, "RHEL", ServiceLevel.PREMIUM, true);
        addBucketToHost(host3, "Satellite", ServiceLevel.PREMIUM, false);

        Host host4 = createHost("insights4", "account2", "org2");
        addBucketToHost(host4, "RHEL", ServiceLevel.SELF_SUPPORT, false);
        addBucketToHost(host4, "SUPER_COOL_PRODUCT", ServiceLevel.ANY, true);

        Host host5 = createHost("insights5", "account2", "org2");
        addBucketToHost(host5, "Satellite", ServiceLevel.SELF_SUPPORT, false);

        // ACCOUNT 3 HOSTS
        Host host6 = createHost("insights6", "account3", "org3");
        addBucketToHost(host6, "RHEL", ServiceLevel.PREMIUM, false);

        Host host7 = createHost("insights7", "account3", "org3");
        addBucketToHost(host7, "RHEL", ServiceLevel.PREMIUM, true);

        // ACCOUNT 4 HOSTS
        Host host8 = createHost("insights8", "account4", "org4");

        List<Host> toSave = Arrays.asList(host1, host2, host3, host4, host5, host6, host7, host8);
        repo.saveAll(toSave);
        repo.flush();
    }

    @Transactional
    @Test
    void testCreate() {
        Host host = new Host("HOST1", "my_acct", "my_org");
        host.addBucket("RHEL", ServiceLevel.PREMIUM, false);
        repo.saveAndFlush(host);

        Optional<Host> result = repo.findById(host.getInsightsId());
        assertTrue(result.isPresent());
        Host saved = result.get();
        assertEquals(1, saved.getBuckets().size());
    }

    @Transactional
    @Test
    void testUpdate() {
        Host host = new Host("HOST1", "my_acct", "my_org");
        host.setSockets(1);
        host.setCores(1);
        host.addBucket("RHEL", ServiceLevel.PREMIUM, false);
        host.addBucket("Satellite", ServiceLevel.PREMIUM, true);
        repo.saveAndFlush(host);

        Optional<Host> result = repo.findById(host.getInsightsId());
        assertTrue(result.isPresent());
        Host toUpdate = result.get();
        assertEquals(2, toUpdate.getBuckets().size());

        toUpdate.setAccountNumber("updated_acct_num");
        toUpdate.setSockets(4);
        toUpdate.setCores(8);
        toUpdate.removeBucket(host.getBuckets().get(0));

        repo.saveAndFlush(toUpdate);

        Optional<Host> updateResult = repo.findById(toUpdate.getInsightsId());
        assertTrue(updateResult.isPresent());
        Host updated = updateResult.get();
        assertEquals("updated_acct_num", updated.getAccountNumber());
        assertEquals(4, updated.getSockets().intValue());
        assertEquals(8, updated.getCores().intValue());
        assertEquals(1, updated.getBuckets().size());
        assertTrue(updated.getBuckets().contains(host.getBuckets().get(1)));
    }

    @Transactional
    @Test
    void findHostsByBucketCriteria() {
        Page<Host> hosts = repo.getHostsByBucketCriteria("account2", "RHEL", ServiceLevel.PREMIUM, true,
            PageRequest.of(0, 10));
        List<Host> found = hosts.stream().collect(Collectors.toList());

        assertEquals(1, found.size());
        assertHost(found.get(0), "insights3", "account2", "org2");
    }

    @Transactional
    @Test
    void findHostsByAnyBucketProduct() {
        Page<Host> hosts = repo.getHostsByBucketCriteria("account2", null, ServiceLevel.SELF_SUPPORT, false,
            PageRequest.of(0, 10));
        Map<String, Host> found = hosts.stream().collect(
            Collectors.toMap(Host::getInsightsId, Function.identity()));

        assertEquals(2, found.size());
        assertHost(found.get("insights4"), "insights4", "account2", "org2");
        assertHost(found.get("insights5"), "insights5", "account2", "org2");
    }

    @Transactional
    @Test
    void findHostsByAnyBucketSla() {
        Page<Host> hosts = repo.getHostsByBucketCriteria("account1", "RHEL", null, false,
            PageRequest.of(0, 10));
        Map<String, Host> found = hosts.stream().collect(
            Collectors.toMap(Host::getInsightsId, Function.identity()));

        assertEquals(2, found.size());
        assertHost(found.get("insights1"), "insights1", "account1", "org1");
        assertHost(found.get("insights2"), "insights2", "account1", "org1");
    }

    @Transactional
    @Test
    void findHostsByAnyBucketAsHypervisor() {
        Page<Host> hosts = repo.getHostsByBucketCriteria("account3", "RHEL", ServiceLevel.PREMIUM, null,
            PageRequest.of(0, 10));
        Map<String, Host> found = hosts.stream().collect(
            Collectors.toMap(Host::getInsightsId, Function.identity()));

        assertEquals(2, found.size());
        assertHost(found.get("insights6"), "insights6", "account3", "org3");
        assertHost(found.get("insights7"), "insights7", "account3", "org3");
    }

    @Transactional
    @Test
    void findHostsWithoutBucketCriterial() {
        Page<Host> hosts = repo.getHostsByBucketCriteria("account2", null, null, null, PageRequest.of(0, 10));
        Map<String, Host> found =
            hosts.stream().collect(Collectors.toMap(Host::getInsightsId, Function.identity()));

        assertEquals(3, found.size());
        assertHost(found.get("insights3"), "insights3", "account2", "org2");
        assertHost(found.get("insights4"), "insights4", "account2", "org2");
        assertHost(found.get("insights5"), "insights5", "account2", "org2");
    }

    @Transactional
    @Test
    void testNoHostFoundWhenItHasNoBucket() {
        Optional<Host> existing = repo.findById("insights8");
        assertTrue(existing.isPresent());
        assertEquals("account4", existing.get().getAccountNumber());

        // When a host has no buckets, it will not be returned.
        Page<Host> hosts = repo.getHostsByBucketCriteria("account4", null, null, null, PageRequest.of(0, 10));
        assertEquals(0, hosts.stream().count());
    }

    private Host createHost(String insightsId, String account, String orgId) {
        Host host = new Host(insightsId, account, orgId);
        host.setSockets(1);
        host.setCores(1);
        return host;
    }

    private HostTallyBucket addBucketToHost(Host host, String productId, ServiceLevel sla,
        Boolean asHypervisor) {
        return host.addBucket(productId, sla, asHypervisor);
    }

    private void assertHost(Host host, String insightsId, String accountNumber, String orgId) {
        assertNotNull(host);
        assertEquals(insightsId, host.getInsightsId());
        assertEquals(accountNumber, host.getAccountNumber());
        assertEquals(orgId, host.getOrgId());
    }
}
