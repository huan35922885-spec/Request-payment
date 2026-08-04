package tw.com.jsgcpa.paymentapproval.payment.service;

import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Registers storage cleanup that must run when the surrounding transaction
 * rolls back.
 */
@Component
public class TransactionRollbackCleanupRegistrar {

    public void register(Runnable cleanup) {
        Objects.requireNonNull(cleanup, "cleanup must not be null");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            cleanup.run();
                        }
                    }
                }
        );
    }
}
