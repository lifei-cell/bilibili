package com.gary.bilibili.common.reliability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class MessageInboxLeaseGuard {

    private final MessageInboxRepository inboxRepository;
    private final TransactionTemplate transaction;

    public MessageInboxLeaseGuard(MessageInboxRepository inboxRepository,
                                  PlatformTransactionManager transactionManager) {
        this.inboxRepository = inboxRepository;
        this.transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Hold the inbox row lock through business writes and the completion marker.
     * A takeover waits for commit/rollback; expired or replaced claims never run the action.
     * Callbacks must join this transaction, not dispatch detached database writes.
     */
    public void run(MessageInboxLease lease, Runnable action) {
        transaction.executeWithoutResult(status -> {
            if (!inboxRepository.lockCurrent(lease)) {
                throw new MessageInboxLeaseLostException(lease);
            }
            action.run();
        });
    }
}
