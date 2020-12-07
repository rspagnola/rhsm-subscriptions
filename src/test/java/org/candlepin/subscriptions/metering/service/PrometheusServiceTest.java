/*
 * Copyright (c) 2020 Red Hat, Inc.
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
package org.candlepin.subscriptions.metering.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.candlepin.subscriptions.prometheus.api.ApiProvider;
import org.candlepin.subscriptions.prometheus.api.StubApiProvider;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.resources.QueryApi;
import org.candlepin.subscriptions.prometheus.resources.QueryRangeApi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URLEncoder;
import java.time.OffsetDateTime;


@ExtendWith(MockitoExtension.class)
public class PrometheusServiceTest {

    @Mock
    private QueryApi queryApi;

    @Mock
    private QueryRangeApi rangeApi;

    @Test
    void testGetOpenshiftMetrics() throws Exception {
        String account = "test-account";
        PrometheusServicePropeties props = new PrometheusServicePropeties();

        String expectedQuery = String.format(props.getOpenshiftMetricsPromQL(), account);
        expectedQuery = URLEncoder.encode(expectedQuery, "UTF-8");
        QueryResult expectedResult = new QueryResult();

        OffsetDateTime end = OffsetDateTime.now();
        OffsetDateTime start = end.minusDays(2);
        String step = "3600";

        when(rangeApi.queryRange(eq(expectedQuery), eq(start.toEpochSecond()), eq(end.toEpochSecond()),
            eq(step), anyInt()))
            .thenReturn(expectedResult);

        ApiProvider provider = new StubApiProvider(queryApi, rangeApi);
        PrometheusService service = new PrometheusService(props, provider);

        QueryResult result = service.getOpenshiftData(account, start, end);
        assertEquals(expectedResult, result);
    }

}
