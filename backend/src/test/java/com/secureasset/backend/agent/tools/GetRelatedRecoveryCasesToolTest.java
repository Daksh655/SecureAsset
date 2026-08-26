package com.secureasset.backend.agent.tools;

import com.secureasset.backend.agent.tools.dto.RelatedRecoveryCasesResult;
import com.secureasset.backend.entity.Order;
import com.secureasset.backend.entity.RecoveryAction;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.repository.RecoveryActionRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GetRelatedRecoveryCasesToolTest {

    private RecoveryCaseRepository caseRepository;
    private RecoveryActionRepository actionRepository;
    private GetRelatedRecoveryCasesTool tool;

    @BeforeEach
    void setUp() {
        caseRepository = mock(RecoveryCaseRepository.class);
        actionRepository = mock(RecoveryActionRepository.class);
        tool = new GetRelatedRecoveryCasesTool(caseRepository, actionRepository);
    }

    @Test
    void missingCustomerIdThrowsException() {
        assertThatThrownBy(() -> tool.execute(new GetRelatedRecoveryCasesTool.Input(null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noRecoveryHistoryReturnsZeros() {
        UUID customerId = UUID.randomUUID();
        when(caseRepository.findByCustomerId(customerId)).thenReturn(Collections.emptyList());

        RelatedRecoveryCasesResult result = tool.execute(new GetRelatedRecoveryCasesTool.Input(customerId, null));

        assertThat(result.totalRelatedCases()).isEqualTo(0);
        assertThat(result.activeCaseCount()).isEqualTo(0);
        assertThat(result.recentCases()).isEmpty();
        assertThat(result.recentActions()).isEmpty();
        assertThat(result.lastRecoveryAttemptAt()).isNull();
    }

    @Test
    void oneActiveRecoveryCaseWithoutActions() {
        UUID customerId = UUID.randomUUID();
        RecoveryCase rc = new RecoveryCase();
        rc.setId(UUID.randomUUID());
        rc.setStatus(RecoveryCase.Status.ACTION_REQUIRED);
        rc.setDetectedAt(OffsetDateTime.now());

        when(caseRepository.findByCustomerId(customerId)).thenReturn(List.of(rc));
        when(actionRepository.findByRecoveryCaseIdOrderByRequestedAtDesc(rc.getId())).thenReturn(Collections.emptyList());

        RelatedRecoveryCasesResult result = tool.execute(new GetRelatedRecoveryCasesTool.Input(customerId, null));

        assertThat(result.totalRelatedCases()).isEqualTo(1);
        assertThat(result.activeCaseCount()).isEqualTo(1);
        assertThat(result.recentCases()).hasSize(1);
        assertThat(result.recentActions()).isEmpty();
    }

    @Test
    void multipleRelatedCasesAndActionsBounded() {
        UUID customerId = UUID.randomUUID();
        
        RecoveryCase rc1 = new RecoveryCase();
        rc1.setId(UUID.randomUUID());
        rc1.setStatus(RecoveryCase.Status.RECOVERED);
        rc1.setDetectedAt(OffsetDateTime.now().minusDays(1));

        RecoveryCase rc2 = new RecoveryCase();
        rc2.setId(UUID.randomUUID());
        rc2.setStatus(RecoveryCase.Status.FAILED);
        rc2.setDetectedAt(OffsetDateTime.now());
        
        RecoveryAction ra1 = new RecoveryAction();
        ra1.setId(UUID.randomUUID());
        ra1.setStatus(RecoveryAction.Status.SUCCESS);
        ra1.setRequestedAt(OffsetDateTime.now().minusHours(24));
        
        RecoveryAction ra2 = new RecoveryAction();
        ra2.setId(UUID.randomUUID());
        ra2.setStatus(RecoveryAction.Status.FAILED);
        ra2.setRequestedAt(OffsetDateTime.now());

        when(caseRepository.findByCustomerId(customerId)).thenReturn(List.of(rc1, rc2));
        when(actionRepository.findByRecoveryCaseIdOrderByRequestedAtDesc(rc1.getId())).thenReturn(List.of(ra1));
        when(actionRepository.findByRecoveryCaseIdOrderByRequestedAtDesc(rc2.getId())).thenReturn(List.of(ra2));

        RelatedRecoveryCasesResult result = tool.execute(new GetRelatedRecoveryCasesTool.Input(customerId, null));

        assertThat(result.totalRelatedCases()).isEqualTo(2);
        assertThat(result.activeCaseCount()).isEqualTo(0);
        assertThat(result.recoveredCaseCount()).isEqualTo(1);
        assertThat(result.failedCaseCount()).isEqualTo(1);
        
        assertThat(result.recentCases()).hasSize(2);
        assertThat(result.recentActions()).hasSize(2);
        
        assertThat(result.lastRecoveryAttemptAt()).isEqualTo(ra2.getRequestedAt());
        assertThat(result.lastRecoveryOutcome()).isEqualTo("FAILED");
    }

    @Test
    void optionalOrderIdFilteringWorks() {
        UUID customerId = UUID.randomUUID();
        UUID orderIdToFind = UUID.randomUUID();
        UUID otherOrderId = UUID.randomUUID();
        
        Order o1 = new Order(); o1.setId(orderIdToFind);
        Order o2 = new Order(); o2.setId(otherOrderId);

        RecoveryCase rc1 = new RecoveryCase();
        rc1.setId(UUID.randomUUID());
        rc1.setOrder(o1);

        RecoveryCase rc2 = new RecoveryCase();
        rc2.setId(UUID.randomUUID());
        rc2.setOrder(o2);

        when(caseRepository.findByCustomerId(customerId)).thenReturn(List.of(rc1, rc2));

        RelatedRecoveryCasesResult result = tool.execute(new GetRelatedRecoveryCasesTool.Input(customerId, orderIdToFind));

        assertThat(result.totalRelatedCases()).isEqualTo(1);
        assertThat(result.recentCases().get(0).id()).isEqualTo(rc1.getId());
    }
}
