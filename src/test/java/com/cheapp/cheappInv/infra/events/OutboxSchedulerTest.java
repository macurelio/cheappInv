package com.cheapp.cheappInv.infra.events;

import com.cheapp.cheappInv.infra.persistence.OutboxEventEntity;
import com.cheapp.cheappInv.infra.persistence.OutboxEventRepository;
import com.cheapp.cheappInv.support.TestClock;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxSchedulerTest {
	@Test
	void pollAndPublish_noHaceNada_siDisabled() {
		OutboxEventRepository repo = mock(OutboxEventRepository.class);
		OutboxPublisher publisher = mock(OutboxPublisher.class);
		Clock clock = TestClock.fixedUtc();

		OutboxScheduler scheduler = new OutboxScheduler(repo, publisher, clock, false, 10);
		scheduler.pollAndPublish();

		verifyNoInteractions(repo, publisher);
	}

	@Test
	void pollAndPublish_publicaYMarcaComoPublicado() {
		OutboxEventRepository repo = mock(OutboxEventRepository.class);
		OutboxPublisher publisher = mock(OutboxPublisher.class);
		Clock clock = TestClock.fixedUtc();

		OutboxEventEntity e1 = new OutboxEventEntity("id-1", "StockRepuesto", 1, "corr", "{}", Instant.now(clock));
		OutboxEventEntity e2 = new OutboxEventEntity("id-2", "StockDescontado", 1, "corr", "{}", Instant.now(clock));
		when(repo.findUnpublished(any())).thenReturn(List.of(e1, e2));

		OutboxScheduler scheduler = new OutboxScheduler(repo, publisher, clock, true, 50);
		scheduler.pollAndPublish();

		verify(publisher).publish("id-1", "StockRepuesto", "corr", "{}");
		verify(publisher).publish("id-2", "StockDescontado", "corr", "{}");
		assertThat(e1.isPublished()).isTrue();
		assertThat(e2.isPublished()).isTrue();
		verify(repo).saveAll(argThat((java.util.List<com.cheapp.cheappInv.infra.persistence.OutboxEventEntity> list) ->
				list.size() == 2 && list.get(0).isPublished() && list.get(1).isPublished()
		));
	}
}
